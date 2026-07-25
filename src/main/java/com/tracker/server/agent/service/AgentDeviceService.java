package com.tracker.server.agent.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tracker.server.agent.dto.AgentEnrollmentRequest;
import com.tracker.server.agent.dto.AgentEnrollmentResponse;
import com.tracker.server.agent.dto.AgentPresenceRequest;
import com.tracker.server.agent.dto.AgentShutdownRequest;
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

    public static final String LIFECYCLE_ONLINE = "ONLINE";
    public static final String LIFECYCLE_OFFLINE = "OFFLINE";
    public static final String LIFECYCLE_SHUTDOWN = "SHUTDOWN";

    private final AgentDeviceRepository agentDeviceRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final AgentCredentialService credentialService;
    private final AgentProjectionService projectionService;

    @Transactional
    public synchronized AgentEnrollmentResponse enroll(
            String username,
            AgentEnrollmentRequest request,
            String remoteAddress) {

        String deviceUuid = canonicalUuid(request.deviceUuid(), "device UUID");
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
                        .legacyDeviceId(findOrCreateLegacyDevice(
                                user, deviceUuid, request, remoteAddress, now).getId())
                        .createdAt(now)
                        .build());

        mapping.setMachineName(request.machineName());
        mapping.setOsName(request.osName());
        mapping.setAgentVersion(request.agentVersion());
        mapping.setLastIpAddress(remoteAddress);
        mapping.setLastSeenAt(now);
        if (mapping.getLifecycleState() == null) {
            mapping.setLifecycleState(LIFECYCLE_OFFLINE);
            mapping.setLastLifecycleAt(now);
        }
        mapping.setUpdatedAt(now);
        String deviceToken = credentialService.issue(mapping);
        mapping = agentDeviceRepository.save(mapping);

        // Enrollment only issues/refreshes credentials. The device becomes ONLINE only
        // after its authoritative local activity backlog has been applied.
        markLegacyOffline(mapping);

        return new AgentEnrollmentResponse(
                mapping.getDeviceUuid(), mapping.getLegacyDeviceId(), deviceToken);
    }

    @Transactional
    public void heartbeat(
            String username,
            String requestedUuid,
            AgentPresenceRequest request,
            String remoteAddress) {
        AgentDevice mapping = requireOwnedForUpdate(username, requestedUuid);
        LocalDateTime serverNow = utcNow();
        String sessionUuid = canonicalUuid(request.sessionUuid(), "session UUID");
        LocalDateTime sessionStartedAt = safeClientTime(
                request.sessionStartedAt(), serverNow, "session start");
        long sessionSequence = requireSessionSequence(request.sessionSequence());
        LocalDateTime observedAt = safeClientTime(
                request.observedAt(), serverNow, "heartbeat time");

        if (!acceptOnlineEvidence(
                mapping,
                sessionUuid,
                sessionStartedAt,
                sessionSequence,
                observedAt,
                serverNow,
                remoteAddress,
                true)) {
            return;
        }

        // OFFLINE never mutates activity rows, so reconnect only needs to mark the
        // device online. Pending local revisions are uploaded by the independent sync worker.
        markLegacyOnline(mapping, remoteAddress, serverNow);
    }

    /**
     * Every activity upload is presence evidence, but old-session uploads remain accepted for
     * record reconciliation without being allowed to resurrect a device after shutdown or after
     * a newer session has started.
     */
    @Transactional
    public AgentDevice activitySeen(
            String username,
            String requestedUuid,
            String sessionUuidValue,
            Instant sessionStartedAtValue,
            long sessionSequenceValue,
            Instant sentAtValue,
            boolean backlogCompleteAfterBatch,
            String remoteAddress) {
        AgentDevice mapping = requireOwnedForUpdate(username, requestedUuid);
        LocalDateTime serverNow = utcNow();
        String sessionUuid = canonicalUuid(sessionUuidValue, "session UUID");
        LocalDateTime sessionStartedAt = safeClientTime(
                sessionStartedAtValue, serverNow, "session start");
        long sessionSequence = requireSessionSequence(sessionSequenceValue);
        LocalDateTime sentAt = safeClientTime(sentAtValue, serverNow, "sync time");

        boolean acceptedAsPresence = acceptOnlineEvidence(
                mapping,
                sessionUuid,
                sessionStartedAt,
                sessionSequence,
                sentAt,
                serverNow,
                remoteAddress,
                backlogCompleteAfterBatch);
        if (acceptedAsPresence) {
            if (LIFECYCLE_ONLINE.equalsIgnoreCase(mapping.getLifecycleState())) {
                markLegacyOnline(mapping, remoteAddress, serverNow);
            } else if (LIFECYCLE_OFFLINE.equalsIgnoreCase(mapping.getLifecycleState())) {
                markLegacyOffline(mapping);
            }
        }
        return mapping;
    }

    @Transactional
    public void shutdown(
            String username,
            String requestedUuid,
            AgentShutdownRequest request,
            String remoteAddress) {
        AgentDevice mapping = requireOwnedForUpdate(username, requestedUuid);
        LocalDateTime serverNow = utcNow();
        String sessionUuid = canonicalUuid(request.sessionUuid(), "session UUID");
        LocalDateTime sessionStartedAt = safeClientTime(
                request.sessionStartedAt(), serverNow, "session start");
        long sessionSequence = requireSessionSequence(request.sessionSequence());
        LocalDateTime shutdownAt = safeClientTime(
                request.shutdownAt(), serverNow, "shutdown time");

        if (!acceptShutdown(
                mapping,
                sessionUuid,
                sessionStartedAt,
                sessionSequence,
                shutdownAt,
                serverNow,
                remoteAddress)) {
            return;
        }

        projectionService.temporarilyCloseForShutdown(mapping, shutdownAt);
        deviceRepository.findById(mapping.getLegacyDeviceId()).ifPresent(device -> {
            device.setLastSeen(max(device.getLastSeen(), shutdownAt));
            device.setLastIpAddress(remoteAddress);
            device.setOnline(false);
            device.setStatus(LIFECYCLE_SHUTDOWN);
            deviceRepository.save(device);
        });
    }

    @Transactional(readOnly = true)
    public AgentDevice requireOwned(String username, String requestedUuid) {
        String deviceUuid = canonicalUuid(requestedUuid, "device UUID");
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

    private AgentDevice requireOwnedForUpdate(String username, String requestedUuid) {
        String deviceUuid = canonicalUuid(requestedUuid, "device UUID");
        User user = requireUser(username);
        return agentDeviceRepository.findOwnedForUpdate(deviceUuid, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Device is not enrolled for this user"));
    }

    private boolean acceptOnlineEvidence(
            AgentDevice mapping,
            String incomingSessionUuid,
            LocalDateTime incomingSessionStartedAt,
            long incomingSessionSequence,
            LocalDateTime eventAt,
            LocalDateTime serverNow,
            String remoteAddress,
            boolean allowOnlineTransition) {

        String currentSessionUuid = mapping.getCurrentSessionUuid();
        boolean sameSession = incomingSessionUuid.equals(currentSessionUuid);

        if (currentSessionUuid != null && !sameSession
                && !isNewerSession(
                        mapping,
                        incomingSessionSequence,
                        incomingSessionStartedAt,
                        eventAt)) {
            // Delayed heartbeat/upload from an older agent run. Its activity records may still be
            // upserted, but it must never change current presence or lifecycle state.
            return false;
        }
        if (sameSession && LIFECYCLE_SHUTDOWN.equalsIgnoreCase(mapping.getLifecycleState())) {
            // Once a session has explicitly shut down, delayed requests from that same session
            // cannot resurrect it. A real restart always creates a fresh session UUID.
            return false;
        }

        if (!sameSession) {
            mapping.setCurrentSessionUuid(incomingSessionUuid);
            mapping.setCurrentSessionStartedAt(incomingSessionStartedAt);
            mapping.setCurrentSessionSequence(incomingSessionSequence);
            if (!allowOnlineTransition) {
                mapping.setLifecycleState(LIFECYCLE_OFFLINE);
            }
        } else {
            if (mapping.getCurrentSessionStartedAt() == null) {
                mapping.setCurrentSessionStartedAt(incomingSessionStartedAt);
            }
            if (mapping.getCurrentSessionSequence() == null) {
                mapping.setCurrentSessionSequence(incomingSessionSequence);
            }
        }
        if (allowOnlineTransition) {
            mapping.setLifecycleState(LIFECYCLE_ONLINE);
        }
        mapping.setLastLifecycleAt(max(mapping.getLastLifecycleAt(), eventAt));
        // lastSeenAt intentionally uses server receipt time so heartbeat timeout is immune to a
        // damaged or incorrectly configured client clock.
        mapping.setLastSeenAt(serverNow);
        mapping.setLastIpAddress(remoteAddress);
        mapping.setUpdatedAt(serverNow);
        agentDeviceRepository.save(mapping);
        return true;
    }

    private boolean acceptShutdown(
            AgentDevice mapping,
            String incomingSessionUuid,
            LocalDateTime incomingSessionStartedAt,
            long incomingSessionSequence,
            LocalDateTime shutdownAt,
            LocalDateTime serverNow,
            String remoteAddress) {

        String currentSessionUuid = mapping.getCurrentSessionUuid();
        boolean sameSession = incomingSessionUuid.equals(currentSessionUuid);

        if (currentSessionUuid != null && !sameSession
                && !isNewerSession(
                        mapping,
                        incomingSessionSequence,
                        incomingSessionStartedAt,
                        shutdownAt)) {
            // A shutdown from an older session arrived after a newer boot. Ignore it completely.
            return false;
        }
        mapping.setCurrentSessionUuid(incomingSessionUuid);
        mapping.setCurrentSessionStartedAt(incomingSessionStartedAt);
        mapping.setCurrentSessionSequence(incomingSessionSequence);
        mapping.setLifecycleState(LIFECYCLE_SHUTDOWN);
        mapping.setLastLifecycleAt(max(mapping.getLastLifecycleAt(), shutdownAt));
        mapping.setLastSeenAt(serverNow);
        mapping.setLastIpAddress(remoteAddress);
        mapping.setUpdatedAt(serverNow);
        agentDeviceRepository.save(mapping);
        return true;
    }

    private static boolean isNewerSession(
            AgentDevice mapping,
            long incomingSessionSequence,
            LocalDateTime incomingSessionStartedAt,
            LocalDateTime incomingEventAt) {
        Long currentSequence = mapping.getCurrentSessionSequence();
        if (currentSequence != null) {
            return incomingSessionSequence > currentSequence;
        }
        LocalDateTime currentStartedAt = mapping.getCurrentSessionStartedAt();
        if (currentStartedAt != null) {
            return !incomingSessionStartedAt.isBefore(currentStartedAt);
        }
        LocalDateTime lastLifecycleAt = mapping.getLastLifecycleAt();
        return lastLifecycleAt == null || incomingEventAt.isAfter(lastLifecycleAt);
    }

    private void markLegacyOnline(
            AgentDevice mapping,
            String remoteAddress,
            LocalDateTime seenAt) {
        Device legacy = deviceRepository.findById(mapping.getLegacyDeviceId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Mapped device no longer exists"));
        if (mapping.getMachineName() != null) {
            legacy.setMachineName(mapping.getMachineName());
        }
        if (mapping.getOsName() != null) {
            legacy.setOsName(mapping.getOsName());
        }
        legacy.setLastIpAddress(remoteAddress);
        legacy.setLastSeen(seenAt);
        legacy.setStatus("ACTIVE");
        legacy.setOnline(true);
        legacy.setUninstalledAt(null);
        deviceRepository.save(legacy);
    }

    private void markLegacyOffline(AgentDevice mapping) {
        deviceRepository.findById(mapping.getLegacyDeviceId()).ifPresent(legacy -> {
            if (mapping.getMachineName() != null) {
                legacy.setMachineName(mapping.getMachineName());
            }
            if (mapping.getOsName() != null) {
                legacy.setOsName(mapping.getOsName());
            }
            if (mapping.getLastIpAddress() != null) {
                legacy.setLastIpAddress(mapping.getLastIpAddress());
            }
            // Keep lastSeen unchanged while offline. It is the exact dashboard duration
            // freeze boundary for all still-open activities.
            legacy.setOnline(false);
            legacy.setStatus(LIFECYCLE_OFFLINE);
            deviceRepository.save(legacy);
        });
    }

    private Device findOrCreateLegacyDevice(
            User user,
            String deviceUuid,
            AgentEnrollmentRequest request,
            String remoteAddress,
            LocalDateTime now) {

        // The durable device UUID is the only server identity. A MAC address can be
        // shared, randomized or copied with a VM image, so it must never merge devices.
        String identity = "UUID:" + deviceUuid;
        var existing = deviceRepository.findByMacAddressAndUserId(identity, user.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Device device = Device.builder()
                .macAddress(identity)
                .machineName(request.machineName())
                .osName(request.osName())
                .lastIpAddress(remoteAddress)
                .lastSeen(now)
                .status(LIFECYCLE_OFFLINE)
                .online(false)
                .user(user)
                .build();
        return deviceRepository.save(device);
    }

    private static LocalDateTime safeClientTime(
            Instant value,
            LocalDateTime serverNow,
            String fieldName) {
        if (value == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        // Offline records are authoritative and may be old. Preserve the exact UTC
        // timestamp supplied by the local durable store.
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static String canonicalUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid " + fieldName, ex);
        }
    }

    private static long requireSessionSequence(long value) {
        if (value < 1L) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid session sequence");
        }
        return value;
    }

    private static LocalDateTime max(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        return first.isAfter(second) ? first : second;
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
