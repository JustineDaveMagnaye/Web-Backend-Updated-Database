package com.rocs.osd.service.user.impl;


import ch.qos.logback.core.encoder.EchoEncoder;
import com.rocs.osd.domain.guest.Guest;
import com.rocs.osd.domain.register.Register;
import com.rocs.osd.domain.employee.Employee;
import com.rocs.osd.domain.external.External;
import com.rocs.osd.domain.student.Student;
import com.rocs.osd.domain.user.User;
import com.rocs.osd.domain.user.principal.UserPrincipal;
import com.rocs.osd.exception.domain.OtpExistsException;
import com.rocs.osd.exception.domain.PersonExistsException;
import com.rocs.osd.exception.domain.UserNotFoundException;
import com.rocs.osd.exception.domain.UsernameExistsException;
import com.rocs.osd.repository.employee.EmployeeRepository;
import com.rocs.osd.repository.external.ExternalRepository;
import com.rocs.osd.repository.student.StudentRepository;
import com.rocs.osd.repository.user.UserRepository;
import com.rocs.osd.service.email.EmailService;
import com.rocs.osd.service.login.attempt.LoginAttemptService;
import com.rocs.osd.service.user.UserService;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.rocs.osd.utils.security.enumeration.Role.*;


@Service
@Transactional
@Qualifier("userDetailsService")
public class UserServiceImpl implements UserService, UserDetailsService {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
    private UserRepository userRepository;
    private StudentRepository studentRepository;
    private EmployeeRepository employeeRepository;
    private ExternalRepository externalRepository;
    private BCryptPasswordEncoder passwordEncoder;
    private LoginAttemptService loginAttemptService;

    private EmailService emailService;

    @Autowired
    public UserServiceImpl(UserRepository userRepository,
                           StudentRepository studentRepository,
                           EmployeeRepository employeeRepository,
                           ExternalRepository externalRepository,
                           BCryptPasswordEncoder passwordEncoder,
                           LoginAttemptService loginAttemptService,
                           EmailService emailService) {
        this.employeeRepository = employeeRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.externalRepository = externalRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.emailService = emailService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = this.userRepository.findUserByUsername(username);
        if (user == null) {
            LOGGER.error("Username not found...");
            throw new UsernameNotFoundException("Username not found.");
        }
        validateLoginAttempt(user);
        user.setLastLoginDate(new Date());
        this.userRepository.save(user);
        UserPrincipal userPrincipal = new UserPrincipal(user);
        LOGGER.info("User information found...");
        return userPrincipal;
    }
    private void validateLoginAttempt(User user) {
        if(!user.isLocked()) {
            if(loginAttemptService.hasExceededMaxAttempts(user.getUsername())) {
                user.setLocked(true);
            } else {
                user.setLocked(false);
            }
        } else {
            loginAttemptService.evictUserFromLoginAttemptCache(user.getUsername());
        }
    }
    @Override
    public Register register(Register register) throws UsernameNotFoundException, UsernameExistsException, MessagingException, PersonExistsException, UserNotFoundException {
        String username = register.getUser().getUsername();
        String password = register.getUser().getPassword();
        validateNewUsername(username);
        validatePassword(password);
        String otp = generateOTP();
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setJoinDate(new Date());
        user.setActive(true);

        String personNumber = "";
        String email = "";
        if (register.getEmployee() != null && register.getEmployee().getEmployeeNumber() != null) {
            personNumber = register.getEmployee().getEmployeeNumber();
            Employee employeeExists = employeeRepository.findByEmployeeNumber(personNumber);
            if(employeeExists != null){
                email = employeeExists.getEmail();
                emailService.sendNewPasswordEmail(email, otp);
                employeeExists.setUser(user);
                user.setOtp(otp);
                user.setLocked(true);
                user.setRole(ROLE_EMPLOYEE.name());
                user.setAuthorities(Arrays.stream(ROLE_EMPLOYEE.getAuthorities()).toList());
                userRepository.save(user);
            }
        } else if (register.getStudent() != null && register.getStudent().getStudentNumber() != null) {
            personNumber = register.getStudent().getStudentNumber();
            Student studentExists = studentRepository.findByStudentNumber(personNumber);
            if(studentExists != null){
                email = studentExists.getEmail();
                emailService.sendNewPasswordEmail(email, otp);
                studentExists.setUser(user);
                user.setOtp(otp);
                user.setLocked(true);
                user.setRole(ROLE_STUDENT.name());
                user.setAuthorities(Arrays.stream(ROLE_STUDENT.getAuthorities()).toList());
                userRepository.save(user);
            }
        } else if (register.getExternal() != null && register.getExternal().getExternalNumber() != null) {
            personNumber = register.getExternal().getExternalNumber();
            External externalExists = externalRepository.findByExternalNumber(personNumber);
            if(externalExists != null){
                email = externalExists.getEmail();
                emailService.sendNewPasswordEmail(email, otp);
                externalExists.setUser(user);
                user.setOtp(otp);
                user.setLocked(true);
                user.setRole(ROLE_EMPLOYEE.name());
                user.setAuthorities(Arrays.stream(ROLE_EMPLOYEE.getAuthorities()).toList());
                userRepository.save(user);
            }
        } else if (register.getGuest() != null && register.getGuest().getGuestNumber() != null) {
            personNumber = register.getGuest().getGuestNumber();
        }
        return register;
    }

    @Override
    public User forgotPassword(User newUser) throws UsernameNotFoundException, MessagingException {
        String username = newUser.getUsername();
        boolean isUsernameExist = userRepository.existsUserByUsername(username);
        if(isUsernameExist){
            User user = userRepository.findUserByUsername(username);
            String otp = generateOTP();
            user.setOtp(otp);
            long userId = newUser.getId();
            Student studentAccount = studentRepository.findByUser_Id(userId);
            Employee employeeAccount = employeeRepository.findByUser_Id(userId);
            External externalAccount = externalRepository.findByUser_Id(userId);
            if(studentAccount != null){
                emailService.sendNewPasswordEmail(studentAccount.getEmail(),otp);
            } else if(employeeAccount != null){
                emailService.sendNewPasswordEmail(employeeAccount.getEmail(),otp);
            } else if(externalAccount != null){
                emailService.sendNewPasswordEmail(externalAccount.getEmail(),otp);
            }
            userRepository.save(user);
            LOGGER.info("Username Found!");
        } else {
            throw new UsernameNotFoundException("Username Not Found!");
        }
        return newUser;
    }

    @Override
    public User forgotUsername(User user) throws UsernameNotFoundException, MessagingException {
        return null;
    }

    @Override
    public User verifyOtpForgotUsername(User newUser) throws UsernameNotFoundException, PersonExistsException, OtpExistsException {
        return null;
    }
    @Override
    public User verifyOtpForgotPassword(User newUser) throws UsernameNotFoundException, PersonExistsException, OtpExistsException {
        String username = newUser.getUsername();
        String newPassword = passwordEncoder.encode(newUser.getPassword());
        String otp = newUser.getOtp();
        User user = userRepository.findUserByUsername(username);
        if(user.getOtp().equals(otp)){
            user.setPassword(newPassword);
            user.setOtp(null);
        } else {
            throw new OtpExistsException("Incorrect OTP code!");
        }
        return newUser;
    }
    @Override
    public boolean verifyOtp(String username, String otp) {
        User user = userRepository.findUserByUsername(username);
        if (user != null && user.getOtp().equals(otp)) {
            user.setLocked(false);
            user.setOtp(null);
            userRepository.save(user);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public List<User> getUsers() {
        return this.userRepository.findAll();
    }

    private void validateNewUsername(String newUsername)
            throws UserNotFoundException, UsernameExistsException, PersonExistsException {
        User userByNewUsername = findUserByUsername(newUsername);
        if (StringUtils.isNotBlank(StringUtils.EMPTY)) {
            User currentUser = findUserByUsername(StringUtils.EMPTY);
            if (currentUser == null) {
                throw new UserNotFoundException("User not found.");
            }
            if (userByNewUsername != null && !userByNewUsername.getId().equals(currentUser.getId())) {
                throw new PersonExistsException("Username already exists.");
            }
        } else {
            if (userByNewUsername != null) {
                throw new PersonExistsException("Username already exists.");
            }
        }
    }
    private void validatePassword(String password) throws PersonExistsException {
        String passwordPattern = ".*[^a-zA-Z0-9].*";
        if (!password.matches(passwordPattern)) {
            throw new PersonExistsException("Please create a stronger password. Password should contain special characters.");
        }
    }
    private String generateOTP() {
        return RandomStringUtils.randomAlphanumeric(10);
    }
    private String generateUserId() {
        return RandomStringUtils.randomNumeric(10);
    }

    @Override
    public User findUserByUsername(String username) {
        return this.userRepository.findUserByUsername(username);
    }


}

