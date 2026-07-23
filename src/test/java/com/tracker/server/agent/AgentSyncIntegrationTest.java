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
import com.tracker.server.agent.dto.AgentPresenceRequest;
import com.tracker.server.agent.dto.AgentShutdownRequest;
import com.tracker.server.agent.dto.AgentSyncRequest;
import com.tracker.server.agent.entity.AgentDevice;
import com.tracker.server.agent.model.ActivityKind;
import com.tracker.server.agent.model.ActivityState;
import com.tracker.server.agent.repository.AgentActivityRepository;
import com.tracker.server.agent.repository.AgentDeviceRepository;
import com.tracker.server.agent.service.AgentRecordUpsertService;
import com.tracker.server.agent.service.AgentProjectionService;
import com.tracker.server.agent.service.ProjectionDuplicateRepairService;
import com.tracker.server.agent.service.AgentCredentialService;
import com.tracker.server.agent.service.AgentDeviceService;
import com.tracker.server.agent.service.AgentSyncService;
import com.tracker.server.entity.Device;
import com.tracker.server.entity.ProcessActivity;
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
    @Autowired AgentProjectionService projectionService;
    @Autowired AgentCredentialService credentialService;
    @Autowired AgentSyncService syncService;
    @Autowired AgentDeviceService deviceService;
    @Autowired ProjectionDuplicateRepairService duplicateRepairService;
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
                new AgentSyncRequest(
                        UUID.randomUUID().toString(),
                        Instant.now().minusSeconds(30),
                        1L,
                        Instant.now(),
                        List.of(invalid, valid)),
                "127.0.0.1");

        assertThat(response.acknowledgements())
                .extracting(acknowledgement -> acknowledgement.status())
                .containsExactly("REJECTED", "APPLIED");
        assertThat(activityRepository.findById(valid.recordUuid())).isPresent();
    }
    @Test
    void temporaryOfflineCloseIsReplacedByLaterAuthoritativeLocalClose() {
        String recordUuid = UUID.randomUUID().toString();
        Instant start = Instant.parse("2026-07-20T10:00:00Z");
        upsertService.apply(agentDevice, user, new ActivitySnapshotRequest(
                recordUuid,
                ActivityKind.PROCESS,
                1,
                start,
                null,
                ActivityState.OPEN,
                null,
                700L,
                "chrome.exe",
                null,
                null));

        LocalDateTime disconnectedAt = LocalDateTime.ofInstant(
                Instant.parse("2026-07-20T11:00:00Z"), ZoneOffset.UTC);
        projectionService.temporarilyCloseForOffline(agentDevice, disconnectedAt);

        var temporary = processRepository.findAll().getFirst();
        assertThat(temporary.getStatus()).isEqualTo("OFFLINE");
        assertThat(temporary.getEndTime()).isEqualTo(disconnectedAt);
        assertThat(temporary.getDurationSeconds()).isEqualTo(3_600L);
        assertThat(activityRepository.findById(recordUuid).orElseThrow().getState())
                .isEqualTo(ActivityState.OPEN);

        Instant actualClose = Instant.parse("2026-07-20T11:30:00Z");
        upsertService.apply(agentDevice, user, new ActivitySnapshotRequest(
                recordUuid,
                ActivityKind.PROCESS,
                2,
                start,
                actualClose,
                ActivityState.CLOSED,
                "PROCESS_EXIT",
                700L,
                "chrome.exe",
                null,
                null));

        var corrected = processRepository.findAll().getFirst();
        assertThat(corrected.getStatus()).isEqualTo("CLOSED");
        assertThat(corrected.getEndTime())
                .isEqualTo(LocalDateTime.ofInstant(actualClose, ZoneOffset.UTC));
        assertThat(corrected.getDurationSeconds()).isEqualTo(5_400L);
    }

    @Test
    void reconnectPresenceRestoresOnlyRecordsStillOpenOnTheAgent() {
        String recordUuid = UUID.randomUUID().toString();
        Instant start = Instant.parse("2026-07-20T12:00:00Z");
        upsertService.apply(agentDevice, user, new ActivitySnapshotRequest(
                recordUuid,
                ActivityKind.ACTIVE_WINDOW,
                1,
                start,
                null,
                ActivityState.OPEN,
                null,
                701L,
                "chrome.exe",
                "Dashboard",
                null));
        projectionService.temporarilyCloseForOffline(
                agentDevice,
                LocalDateTime.ofInstant(start.plusSeconds(60), ZoneOffset.UTC));

        projectionService.reconcileOpenRecords(agentDevice, java.util.Set.of(recordUuid));

        var restored = windowRepository.findAll().getFirst();
        assertThat(restored.getStatus()).isEqualTo("RUNNING");
        assertThat(restored.getEndTime()).isNull();
        assertThat(restored.getDurationSeconds()).isNull();
    }

    @Test
    void deviceSessionUsesShutdownStatusOnlyForARealSystemShutdown() {
        Instant start = Instant.parse("2026-07-20T13:00:00Z");
        String shutdownSession = UUID.randomUUID().toString();
        upsertService.apply(agentDevice, user, new ActivitySnapshotRequest(
                shutdownSession,
                ActivityKind.DEVICE_SESSION,
                1,
                start,
                start.plusSeconds(60),
                ActivityState.CLOSED,
                "SYSTEM_SHUTDOWN",
                null,
                null,
                null,
                null));

        assertThat(sessionRepository.findAll().getFirst().getStatus()).isEqualTo("SHUTDOWN");
    }

    @Test
    void lifecycleOrderingRejectsLateRequestsFromAnOldSession() {
        String oldSession = UUID.randomUUID().toString();
        Instant oldStart = Instant.now().minusSeconds(3_600);
        Instant oldShutdown = Instant.now().minusSeconds(1_800);

        deviceService.activitySeen(
                user.getUsername(),
                agentDevice.getDeviceUuid(),
                oldSession,
                oldStart,
                1L,
                oldStart.plusSeconds(30),
                "127.0.0.1");
        deviceService.shutdown(
                user.getUsername(),
                agentDevice.getDeviceUuid(),
                new AgentShutdownRequest(
                        oldShutdown, oldSession, oldStart, 1L, "SYSTEM_SHUTDOWN"),
                "127.0.0.1");

        // A delayed heartbeat from the already shut-down session cannot resurrect it.
        deviceService.heartbeat(
                user.getUsername(),
                agentDevice.getDeviceUuid(),
                new AgentPresenceRequest(
                        oldShutdown.plusSeconds(1), oldSession, oldStart, 1L, List.of()),
                "127.0.0.1");
        var afterLateHeartbeat = agentDeviceRepository.findById(agentDevice.getId()).orElseThrow();
        assertThat(afterLateHeartbeat.getLifecycleState()).isEqualTo("SHUTDOWN");
        assertThat(deviceRepository.findById(agentDevice.getLegacyDeviceId()).orElseThrow().isOnline())
                .isFalse();

        String newSession = UUID.randomUUID().toString();
        Instant newStart = Instant.now();
        deviceService.heartbeat(
                user.getUsername(),
                agentDevice.getDeviceUuid(),
                new AgentPresenceRequest(newStart, newSession, newStart, 2L, List.of()),
                "127.0.0.1");

        // A late shutdown from the older session cannot close the new boot.
        deviceService.shutdown(
                user.getUsername(),
                agentDevice.getDeviceUuid(),
                new AgentShutdownRequest(
                        oldShutdown.plusSeconds(10),
                        oldSession,
                        oldStart,
                        1L,
                        "SYSTEM_SHUTDOWN"),
                "127.0.0.1");

        var current = agentDeviceRepository.findById(agentDevice.getId()).orElseThrow();
        assertThat(current.getCurrentSessionUuid()).isEqualTo(newSession);
        assertThat(current.getLifecycleState()).isEqualTo("ONLINE");
        assertThat(deviceRepository.findById(agentDevice.getLegacyDeviceId()).orElseThrow().isOnline())
                .isTrue();
    }

    @Test
    void serverTruncatesLongWindowTitlesBeforePersistence() {
        String recordUuid = UUID.randomUUID().toString();
        Instant start = Instant.parse("2026-07-22T08:00:00Z");
        String longTitle = "😀".repeat(1_100);

        upsertService.apply(agentDevice, user, new ActivitySnapshotRequest(
                recordUuid,
                ActivityKind.ACTIVE_WINDOW,
                1,
                start,
                null,
                ActivityState.OPEN,
                null,
                800L,
                "chrome.exe",
                longTitle,
                null));

        var canonical = activityRepository.findById(recordUuid).orElseThrow();
        assertThat(canonical.getWindowTitle().codePointCount(
                0, canonical.getWindowTitle().length())).isEqualTo(1_000);
        var projection = windowRepository.findAll().getFirst();
        assertThat(projection.getWindowTitle().codePointCount(
                0, projection.getWindowTitle().length())).isEqualTo(1_000);
    }

    @Test
    void differentCanonicalUuidsForTheSameProcessShareOneProjectionRow() {
        Instant start = Instant.parse("2026-07-22T17:38:37Z");
        ActivitySnapshotRequest first = new ActivitySnapshotRequest(
                UUID.randomUUID().toString(),
                ActivityKind.PROCESS,
                1,
                start,
                null,
                ActivityState.OPEN,
                null,
                11056L,
                "Taskmgr.exe",
                null,
                null);
        ActivitySnapshotRequest repeated = new ActivitySnapshotRequest(
                UUID.randomUUID().toString(),
                ActivityKind.PROCESS,
                1,
                start.plusMillis(500),
                null,
                ActivityState.OPEN,
                null,
                11056L,
                "Taskmgr.exe",
                null,
                null);

        upsertService.apply(agentDevice, user, first);
        upsertService.apply(agentDevice, user, repeated);

        assertThat(activityRepository.count()).isEqualTo(2);
        assertThat(processRepository.count()).isEqualTo(1);
        Long projectionId = processRepository.findAll().getFirst().getId();
        assertThat(activityRepository.findById(first.recordUuid()).orElseThrow().getLegacyRecordId())
                .isEqualTo(projectionId);
        assertThat(activityRepository.findById(repeated.recordUuid()).orElseThrow().getLegacyRecordId())
                .isEqualTo(projectionId);
    }

    @Test
    void startupRepairMergesExistingDuplicateProcessRows() {
        Device device = deviceRepository.findById(agentDevice.getLegacyDeviceId()).orElseThrow();
        LocalDateTime start = LocalDateTime.ofInstant(
                Instant.parse("2026-07-22T17:38:37Z"), ZoneOffset.UTC);
        processRepository.save(ProcessActivity.builder()
                .pid(11056L)
                .processName("Taskmgr.exe")
                .startTime(start)
                .status("RUNNING")
                .device(device)
                .user(user)
                .build());
        processRepository.save(ProcessActivity.builder()
                .pid(11056L)
                .processName("Taskmgr.exe")
                .startTime(start)
                .status("RUNNING")
                .device(device)
                .user(user)
                .build());

        duplicateRepairService.repairAll();

        assertThat(processRepository.findByDeviceIdAndPidAndStartTimeOrderByIdDesc(
                device.getId(), 11056L, start)).singleElement()
                .satisfies(row -> {
                    assertThat(row.getStatus()).isEqualTo("RUNNING");
                    assertThat(row.getEndTime()).isNull();
                    assertThat(row.getDurationSeconds()).isNull();
                });
    }


    @Test
    void duplicateRepairPreservesPidReuseHistoryAndLeavesOnlyNewestRunning() {
        Device device = deviceRepository.findById(agentDevice.getLegacyDeviceId()).orElseThrow();
        LocalDateTime oldStart = LocalDateTime.ofInstant(
                Instant.parse("2026-07-22T17:00:00Z"), ZoneOffset.UTC);
        LocalDateTime newStart = oldStart.plusMinutes(5);
        processRepository.save(ProcessActivity.builder()
                .pid(2000L)
                .processName("old-app.exe")
                .startTime(oldStart)
                .status("RUNNING")
                .device(device)
                .user(user)
                .build());
        processRepository.save(ProcessActivity.builder()
                .pid(2000L)
                .processName("new-app.exe")
                .startTime(newStart)
                .status("RUNNING")
                .device(device)
                .user(user)
                .build());

        duplicateRepairService.repairAll();

        var rows = processRepository.findByDeviceIdAndPidAndStatusOrderByStartTimeAscIdAsc(
                device.getId(), 2000L, "RUNNING");
        assertThat(rows).singleElement()
                .satisfies(row -> assertThat(row.getProcessName()).isEqualTo("new-app.exe"));
        var older = processRepository.findAll().stream()
                .filter(row -> "old-app.exe".equals(row.getProcessName()))
                .findFirst()
                .orElseThrow();
        assertThat(older.getStatus()).isEqualTo("INTERRUPTED");
        assertThat(older.getEndTime()).isEqualTo(newStart);
        assertThat(older.getDurationSeconds()).isEqualTo(300L);
    }

    @Test
    void heartbeatOlderThanActivityStartDoesNotCreateNegativeDuration() {
        String recordUuid = UUID.randomUUID().toString();
        Instant start = Instant.parse("2026-07-22T10:00:30Z");
        upsertService.apply(agentDevice, user, new ActivitySnapshotRequest(
                recordUuid,
                ActivityKind.ACTIVE_WINDOW,
                1,
                start,
                null,
                ActivityState.OPEN,
                null,
                801L,
                "chrome.exe",
                "Latest window",
                null));

        projectionService.temporarilyCloseForOffline(
                agentDevice,
                LocalDateTime.ofInstant(
                        Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC));

        var temporary = windowRepository.findAll().getFirst();
        assertThat(temporary.getStatus()).isEqualTo("OFFLINE");
        assertThat(temporary.getEndTime()).isNull();
        assertThat(temporary.getDurationSeconds()).isNull();

        Instant actualEnd = Instant.parse("2026-07-22T10:01:00Z");
        upsertService.apply(agentDevice, user, new ActivitySnapshotRequest(
                recordUuid,
                ActivityKind.ACTIVE_WINDOW,
                2,
                start,
                actualEnd,
                ActivityState.CLOSED,
                "WINDOW_CHANGED",
                801L,
                "chrome.exe",
                "Latest window",
                null));

        var corrected = windowRepository.findAll().getFirst();
        assertThat(corrected.getStatus()).isEqualTo("CLOSED");
        assertThat(corrected.getEndTime())
                .isEqualTo(LocalDateTime.ofInstant(actualEnd, ZoneOffset.UTC));
        assertThat(corrected.getDurationSeconds()).isEqualTo(30L);
    }

}
