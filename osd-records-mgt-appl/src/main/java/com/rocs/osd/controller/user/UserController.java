package com.rocs.osd.controller.user;

import com.rocs.osd.domain.register.Register;
import com.rocs.osd.domain.user.User;
import com.rocs.osd.domain.user.principal.UserPrincipal;
import com.rocs.osd.exception.domain.*;
import com.rocs.osd.service.user.UserService;
import com.rocs.osd.utils.security.jwt.provider.token.JWTTokenProvider;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.rocs.osd.utils.security.constant.SecurityConstant.JWT_TOKEN_HEADER;
/**
 *RestController for managing user operations such as GET, POST, and PUT requests.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private AuthenticationManager authenticationManager;
    private JWTTokenProvider jwtTokenProvider;

    /**
     * Constructs a new UserController with the provided services.
     *
     * @param userService service handling user operations
     * @param authenticationManager handles authentication
     * @param jwtTokenProvider provides JWT token
     */
    @Autowired
    public UserController(UserService userService, AuthenticationManager authenticationManager, JWTTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Registers a new user.
     *
     * @param register user to register
     * @return the newly registered user
     * @throws UsernameExistsException if username already exists
     * @throws EmailExistsException if email already exists
     * @throws MessagingException if an error occurs during email operations
     * @throws PersonExistsException if the person already exists
     * @throws UserNotFoundException if the user is not found
     * @throws UsernameNotFoundException if the username is not found
     */
    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody Register register)
            throws UsernameNotFoundException, UsernameExistsException, EmailExistsException, MessagingException, PersonExistsException, UserNotFoundException {
        Register registered = this.userService.register(register);
        return new ResponseEntity<>(registered.getUser(), HttpStatus.OK);
    }

    /**
     * Initiates a password reset process.
     *
     * @param user user requesting the password reset
     * @return user with reset password
     * @throws UsernameNotFoundException if the username is not found
     * @throws EmailExistsException if email is not found
     * @throws MessagingException if email sending fails
     * @throws PersonExistsException if the person does not exist
     * @throws UserNotFoundException if the user is not found
     * @throws UsernameExistsException if the username already exist
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<User> forgotPassword(@RequestBody User user)
            throws UsernameNotFoundException, UsernameExistsException, EmailExistsException, MessagingException, PersonExistsException, UserNotFoundException {
        User newUser = this.userService.forgotPassword(user);
        return new ResponseEntity<>(newUser, HttpStatus.OK);
    }

    /**
     * Verifies OTP for password reset.
     *
     * @param user user verifying the OTP
     * @return user if OTP verification is successful
     * @throws UsernameNotFoundException if the username is not found
     * @throws PersonExistsException if the person is not found
     * @throws OtpExistsException if the OTP is incorrect
     */
    @PostMapping("/verify-forgot-password")
    public ResponseEntity<User> verifyForgotPassword(@RequestBody User user)
            throws UsernameNotFoundException, PersonExistsException, OtpExistsException {
        User newUser = this.userService.verifyOtpForgotPassword(user);
        return new ResponseEntity<>(newUser, HttpStatus.OK);
    }

    /**
     * Verifies OTP to unlock the user account.
     *
     * @param request contains username and OTP
     * @return success or failure message based on OTP validation
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String otp = request.get("otp");

        if (username == null || otp == null) {
            return new ResponseEntity<>("Both username and otp are required", HttpStatus.BAD_REQUEST);
        }
        boolean isVerified = userService.verifyOtp(username, otp);
        if (isVerified) {
            return new ResponseEntity<>("Account unlocked successfully", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Invalid OTP", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Log in the user and generates a JWT token.
     *
     * @param user user attempting to log in
     * @return the logged-in user with JWT in headers
     * @throws UsernameNotFoundException if the username is not found
     */
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody User user) throws UsernameNotFoundException{
        authenticate(user.getUsername(), user.getPassword());
        User loginUser = userService.findUserByUsername(user.getUsername());
        UserPrincipal userPrincipal = new UserPrincipal(loginUser);
        HttpHeaders jwtHeaders = getJwtHeader(userPrincipal);
        return new ResponseEntity<>("login success....", jwtHeaders, HttpStatus.OK);
    }

    /**
     * Retrieves the list of all users.
     *
     * @return list of all users
     */
    @GetMapping("/list")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userService.getUsers();
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    private void authenticate(String username, String password) {
        this.authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
    }

    private HttpHeaders getJwtHeader(UserPrincipal userPrincipal) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(JWT_TOKEN_HEADER, jwtTokenProvider.generateJwtToken(userPrincipal));
        return headers;
    }
}
