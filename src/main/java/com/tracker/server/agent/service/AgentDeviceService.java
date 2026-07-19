package com.tracker.server.agent.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tracker.server.agent.dto.AgentEnrollmentRequest;
import com.tracker.server.agent.dto.AgentEnrollmentResponse;
import com.tracker.server.agent.entity.AgentDevice;
import com.tracker.server.agent.repository.AgentDeviceRepository;
import com.tracker.server.entity.Device;
import com.tracker.server.entity.User;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentDeviceService {

    private final AgentDeviceRepository agentDeviceRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final AgentCredentialService credentialService;

    @Transactional
    public AgentEnrollmentResponse enroll(
            String username,
            AgentEnrollmentRequest request,
            String remoteAddress) {

        String deviceUuid = canonicalUuid(request.deviceUuid());
        User user = requireUser(username);
        LocalDateTime now = utcNow();

        AgentDevice mapping = agentDeviceRepository.findByDeviceUuid(deviceUuid)
                .map(existing -> {
                    if (!existing.getUserId().equals(user.getId())) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Device UUID is already enrolled by another user");
                    }
                    return existing;
                })
                .orElseGet(() -> AgentDevice.builder()
                        .deviceUuid(deviceUuid)
                        .userId(user.getId())
                        .legacyDeviceId(findOrCreateLegacyDevice(user, request, remoteAddress, now).getId())
                        .createdAt(now)
                        .build());

        mapping.setMachineName(request.machineName());
        mapping.setOsName(request.osName());
        mapping.setAgentVersion(request.agentVersion());
        mapping.setLastIpAddress(remoteAddress);
        mapping.setLastSeenAt(now);
        mapping.setUpdatedAt(now);
        String deviceToken = credentialService.issue(mapping);
        mapping = agentDeviceRepository.save(mapping);

        Device legacy = deviceRepository.findById(mapping.getLegacyDeviceId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Mapped device no longer exists"));
        legacy.setMachineName(request.machineName());
        legacy.setOsName(request.osName());
        legacy.setLastIpAddress(remoteAddress);
        legacy.setLastSeen(now);
        legacy.setStatus("ACTIVE");
        legacy.setOnline(true);
        legacy.setUninstalledAt(null);
        deviceRepository.save(legacy);

        return new AgentEnrollmentResponse(
                mapping.getDeviceUuid(), mapping.getLegacyDeviceId(), deviceToken);
    }

    @Transactional
    public void heartbeat(String username, String requestedUuid, String remoteAddress) {
        AgentDevice mapping = requireOwned(username, requestedUuid);
        LocalDateTime now = utcNow();
        mapping.setLastSeenAt(now);
        mapping.setLastIpAddress(remoteAddress);
        mapping.setUpdatedAt(now);
        agentDeviceRepository.save(mapping);

        deviceRepository.findById(mapping.getLegacyDeviceId()).ifPresent(device -> {
            device.setLastSeen(now);
            device.setLastIpAddress(remoteAddress);
            device.setOnline(true);
            deviceRepository.save(device);
        });
    }

    @Transactional(readOnly = true)
    public AgentDevice requireOwned(String username, String requestedUuid) {
        String deviceUuid = canonicalUuid(requestedUuid);
        User user = requireUser(username);
        return agentDeviceRepository.findByDeviceUuidAndUserId(deviceUuid, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Device is not enrolled for this user"));
    }

    @Transactional(readOnly = true)
    public User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
    }

    private Device findOrCreateLegacyDevice(
            User user,
            AgentEnrollmentRequest request,
            String remoteAddress,
            LocalDateTime now) {

        String macAddress = normalizedMac(request.macAddress());
        if (macAddress != null) {
            var existing = deviceRepository.findByMacAddressAndUserId(macAddress, user.getId());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Device device = Device.builder()
                .macAddress(macAddress == null ? "UUID:" + request.deviceUuid() : macAddress)
                .machineName(request.machineName())
                .osName(request.osName())
                .lastIpAddress(remoteAddress)
                .lastSeen(now)
                .status("ACTIVE")
                .online(true)
                .user(user)
                .build();
        return deviceRepository.save(device);
    }

    private static String canonicalUuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID", ex);
        }
    }

    private static String normalizedMac(String value) {
        if (value == null || value.isBlank() || "Unknown".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
