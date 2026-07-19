package com.tracker.server.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.tracker.server.agent.dto.ActivitySnapshotRequest;
import com.tracker.server.agent.dto.AgentSyncRequest;
import com.tracker.server.agent.entity.AgentDevice;
import com.tracker.server.agent.model.ActivityKind;
import com.tracker.server.agent.model.ActivityState;
import com.tracker.server.agent.repository.AgentActivityRepository;
import com.tracker.server.agent.repository.AgentDeviceRepository;
import com.tracker.server.agent.service.AgentRecordUpsertService;
import com.tracker.server.agent.service.AgentCredentialService;
import com.tracker.server.agent.service.AgentSyncService;
import com.tracker.server.entity.Device;
import com.tracker.server.entity.User;
import com.tracker.server.repository.ActiveWindowActivityRepository;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.repository.DeviceSessionRepository;
import com.tracker.server.repository.IdleActivityRepository;
import com.tracker.server.repository.ProcessActivityRepository;
import com.tracker.server.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class AgentSyncIntegrationTest {

    @Autowired AgentRecordUpsertService upsertService;
    @Autowired AgentCredentialService credentialService;
    @Autowired AgentSyncService syncService;
    @Autowired AgentActivityRepository activityRepository;
    @Autowired AgentDeviceRepository agentDeviceRepository;
    @Autowired ProcessActivityRepository processRepository;
    @Autowired ActiveWindowActivityRepository windowRepository;
    @Autowired IdleActivityRepository idleRepository;
    @Autowired DeviceSessionRepository sessionRepository;
    @Autowired DeviceRepository deviceRepository;
    @Autowired UserRepository userRepository;

    private User user;
    private AgentDevice agentDevice;

    @BeforeEach
    void setUp() {
        activityRepository.deleteAll();
        processRepository.deleteAll();
        windowRepository.deleteAll();
        idleRepository.deleteAll();
        sessionRepository.deleteAll();
        agentDeviceRepository.deleteAll();
        deviceRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .username("sync-user")
                .password("unused")
                .role("USER")
                .build());
        Device legacy = deviceRepository.save(Device.builder()
                .macAddress("00-11-22-33-44-55")
                .machineName("test-machine")
                .osName("Windows")
                .status("ACTIVE")
                .online(true)
                .user(user)
                .build());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        agentDevice = agentDeviceRepository.save(AgentDevice.builder()
                .deviceUuid(UUID.randomUUID().toString())
                .legacyDeviceId(legacy.getId())
                .userId(user.getId())
                .machineName("test-machine")
                .osName("Windows")
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Test
    void replayAndOutOfOrderUpdatesRemainIdempotent() {
        String recordUuid = UUID.randomUUID().toString();
        Instant start = Instant.parse("2026-07-19T08:00:00Z");
        ActivitySnapshotRequest open = new ActivitySnapshotRequest(
                recordUuid,
                ActivityKind.PROCESS,
                1,
                start,
                null,
                ActivityState.OPEN,
                null,
                4242L,
                "notepad.exe",
                null,
                null);

        assertThat(upsertService.apply(agentDevice, user, open).status()).isEqualTo("APPLIED");
        assertThat(upsertService.apply(agentDevice, user, open).status()).isEqualTo("UNCHANGED");
        assertThat(activityRepository.count()).isEqualTo(1);
        assertThat(processRepository.count()).isEqualTo(1);

        ActivitySnapshotRequest closed = new ActivitySnapshotRequest(
                recordUuid,
                ActivityKind.PROCESS,
                2,
                start,
                start.plusSeconds(10),
                ActivityState.CLOSED,
                "PROCESS_EXIT",
                4242L,
                "notepad.exe",
                null,
                null);
        assertThat(upsertService.apply(agentDevice, user, closed).status()).isEqualTo("APPLIED");
        assertThat(upsertService.apply(agentDevice, user, open).status()).isEqualTo("STALE");

        var canonical = activityRepository.findById(recordUuid).orElseThrow();
        assertThat(canonical.getRevision()).isEqualTo(2);
        assertThat(canonical.getState()).isEqualTo(ActivityState.CLOSED);
        assertThat(canonical.getDurationMillis()).isEqualTo(10_000L);
        assertThat(processRepository.count()).isEqualTo(1);
        var projection = processRepository.findAll().getFirst();
        assertThat(projection.getStatus()).isEqualTo("CLOSED");
        assertThat(projection.getDurationSeconds()).isEqualTo(10L);
    }

    @Test
    void activeWindowKeepsProcessAndTitleInCanonicalRecord() {
        String recordUuid = UUID.randomUUID().toString();
        Instant start = Instant.parse("2026-07-19T09:00:00Z");
        ActivitySnapshotRequest window = new ActivitySnapshotRequest(
                recordUuid,
                ActivityKind.ACTIVE_WINDOW,
                1,
                start,
                start.plusSeconds(2),
                ActivityState.CLOSED,
                "WINDOW_CHANGED",
                99L,
                "explorer.exe",
                "Downloads",
                null);

        upsertService.apply(agentDevice, user, window);

        var canonical = activityRepository.findById(recordUuid).orElseThrow();
        assertThat(canonical.getProcessName()).isEqualTo("explorer.exe");
        assertThat(canonical.getWindowTitle()).isEqualTo("Downloads");
        assertThat(windowRepository.count()).isEqualTo(1);
    }

    @Test
    void deviceCredentialIsRandomHashedAndDeviceScoped() {
        String token = credentialService.issue(agentDevice);
        agentDeviceRepository.save(agentDevice);

        assertThat(token).isNotBlank();
        assertThat(agentDevice.getCredentialHash()).hasSize(64).doesNotContain(token);
        assertThat(credentialService.authenticate(agentDevice.getDeviceUuid(), token).getId())
                .isEqualTo(user.getId());
        assertThatThrownBy(() -> credentialService.authenticate(
                agentDevice.getDeviceUuid(), token + "invalid"))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    @Test
    void rejectedRecordDoesNotBlockOtherRecordsInTheBatch() {
        Instant start = Instant.parse("2026-07-19T10:00:00Z");
        ActivitySnapshotRequest invalid = new ActivitySnapshotRequest(
                UUID.randomUUID().toString(),
                ActivityKind.IDLE,
                2,
                start,
                null,
                ActivityState.CLOSED,
                "USER_ACTIVE",
                null,
                null,
                null,
                null);
        ActivitySnapshotRequest valid = new ActivitySnapshotRequest(
                UUID.randomUUID().toString(),
                ActivityKind.PROCESS,
                1,
                start,
                null,
                ActivityState.OPEN,
                null,
                123L,
                "notepad.exe",
                null,
                null);

        var response = syncService.synchronize(
                user.getUsername(),
                agentDevice.getDeviceUuid(),
                new AgentSyncRequest(List.of(invalid, valid)));

        assertThat(response.acknowledgements())
                .extracting(acknowledgement -> acknowledgement.status())
                .containsExactly("REJECTED", "APPLIED");
        assertThat(activityRepository.findById(valid.recordUuid())).isPresent();
    }
}
