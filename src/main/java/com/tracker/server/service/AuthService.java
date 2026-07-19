package com.tracker.server.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tracker.server.dto.AdminLoginRequest;
import com.tracker.server.dto.LoginRequest;
import com.tracker.server.dto.LoginResponse;
import com.tracker.server.dto.PasswordResetRequest;
import com.tracker.server.dto.RegisterRequest;
import com.tracker.server.entity.User;
import com.tracker.server.repository.UserRepository;
import com.tracker.server.security.JwtUtil;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;

    public String register(RegisterRequest request) {
        String username = request.getUsername().trim();

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Username already registered");
        }

        User user = User.builder()
                .username(username)
//                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .build();

        userRepository.save(user);

        return "User Registered";
    }
    
    
    
    public String adminRegister(
            AdminLoginRequest request) {
        String username = request.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Username already registered");
        }

        User user =
                User.builder()
                        .username(username)
                        .password(
                                passwordEncoder.encode(
                                        request.getPassword()))
                       

                        .role(
                                "ADMIN")
                        .build();

        userRepository.save(user);

        return "admin Registered";
    }

    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid credentials"));

//        if (user.getPassword() == null
//                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
//            throw new ResponseStatusException(
//                    HttpStatus.UNAUTHORIZED, "Invalid credentials");
//        }

        String token =
                jwtUtil.generateToken(
                        user.getUsername(), user.getId(), user.getRole());

        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(
                        user.getUsername())
                .role(user.getRole())
                .build();
    }
    
    
    public LoginResponse adminLogin(
            AdminLoginRequest request) {

        User user =
                userRepository.findByUsername(
                                request.getUsername().trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    

        if (!"ADMIN".equalsIgnoreCase(user.getRole())
                || user.getPassword() == null
                || !passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token =
                jwtUtil.generateToken(
                        user.getUsername(), user.getId(), user.getRole());

        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(
                        user.getUsername())
                .role(user.getRole())
                .build();
    }

    @Transactional
    public void resetPassword(Long userId, PasswordResetRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);
    }
}
