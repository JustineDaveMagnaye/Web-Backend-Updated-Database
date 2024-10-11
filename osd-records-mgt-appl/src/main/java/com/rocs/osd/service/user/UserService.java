package com.rocs.osd.service.user;


import com.rocs.osd.domain.register.Register;
import com.rocs.osd.domain.user.User;
import com.rocs.osd.exception.domain.*;
import jakarta.mail.MessagingException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

public interface UserService {
    Register register(Register register) throws UsernameNotFoundException, UsernameExistsException, EmailExistsException, MessagingException, PersonExistsException, UserNotFoundException;

    User forgotPassword(User user) throws UsernameNotFoundException, MessagingException;

    User forgotUsername(User user) throws UsernameNotFoundException, MessagingException;

    List<User> getUsers();

    User findUserByUsername(String username);

    User verifyOtpForgotPassword(User newUser) throws PersonExistsException, OtpExistsException;

    User verifyOtpForgotUsername(User newUser) throws PersonExistsException, OtpExistsException;

    boolean verifyOtp(String username, String otp);

}
