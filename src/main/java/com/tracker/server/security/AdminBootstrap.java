package com.tracker.server.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.tracker.server.entity.User;
import com.tracker.server.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${tracker.bootstrap-admin.username:}")
    private String username;

    @Value("${tracker.bootstrap-admin.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments arguments) {
        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            return;
        }
        if (password.length() < 12) {
            log.error("Bootstrap admin password must contain at least 12 characters");
            return;
        }

        String normalizedUsername = username.trim();
        userRepository.findByUsername(normalizedUsername).ifPresentOrElse(existing -> {
            if (!"ADMIN".equalsIgnoreCase(existing.getRole())) {
                log.error("Bootstrap username already belongs to a non-admin account");
            }
        }, () -> {
            userRepository.save(User.builder()
                    .username(normalizedUsername)
                    .password(passwordEncoder.encode(password))
                    .role("ADMIN")
                    .build());
            log.warn("Initial administrator account created; remove bootstrap secrets now");
        });
    }
}
