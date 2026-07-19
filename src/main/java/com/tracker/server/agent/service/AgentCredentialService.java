package com.tracker.server.agent.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tracker.server.agent.entity.AgentDevice;
import com.tracker.server.agent.repository.AgentDeviceRepository;
import com.tracker.server.entity.User;
import com.tracker.server.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentCredentialService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AgentDeviceRepository agentDeviceRepository;
    private final UserRepository userRepository;

    public String issue(AgentDevice device) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        device.setCredentialHash(HexFormat.of().formatHex(digest(token)));
        return token;
    }

    @Transactional(readOnly = true)
    public User authenticate(String requestedUuid, String token) {
        if (token == null || token.isBlank()) {
            throw invalidCredential();
        }

        String deviceUuid;
        try {
            deviceUuid = UUID.fromString(requestedUuid).toString();
        } catch (RuntimeException ex) {
            throw invalidCredential();
        }

        AgentDevice device = agentDeviceRepository.findByDeviceUuid(deviceUuid)
                .orElseThrow(AgentCredentialService::invalidCredential);
        if (!matches(device.getCredentialHash(), token)) {
            throw invalidCredential();
        }
        return userRepository.findById(device.getUserId())
                .orElseThrow(AgentCredentialService::invalidCredential);
    }

    private static boolean matches(String expectedHex, String token) {
        if (expectedHex == null || expectedHex.isBlank()) {
            return false;
        }
        try {
            return MessageDigest.isEqual(
                    HexFormat.of().parseHex(expectedHex), digest(token));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static BadCredentialsException invalidCredential() {
        return new BadCredentialsException("Invalid agent credential");
    }
}
