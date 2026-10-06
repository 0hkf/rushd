package com.rushd.service;

import com.rushd.dto.LoginRequest;
import com.rushd.dto.RegisterRequest;
import com.rushd.dto.UserResponse;
import com.rushd.entity.User;
import com.rushd.entity.Role;
import org.springframework.security.access.AccessDeniedException;
import com.rushd.exception.EmailAlreadyExistsException;
import com.rushd.exception.InvalidCredentialsException;
import com.rushd.exception.UserNotFoundException;
import com.rushd.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    public UserResponse register(RegisterRequest request) {
        if (request.getRole() != Role.BUYER) {
            throw new AccessDeniedException("Public registration is only available for regular users");
        }
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());

        return UserResponse.from(userRepository.save(user));
    }

    public User authenticate(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            normalizedEmail, request.getPassword()));
        } catch (AuthenticationException invalid) {
            throw new InvalidCredentialsException();
        }
        return userRepository.findByEmail(normalizedEmail).orElseThrow(InvalidCredentialsException::new);
    }

    public UserResponse me(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
        return UserResponse.from(user);
    }
}
