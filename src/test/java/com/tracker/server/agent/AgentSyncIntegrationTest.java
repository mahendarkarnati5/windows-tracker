package com.tracker.server.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.tracker.server.agent.dto.ActivitySnapshotRequest;
import com.tracker.server.agent.dto.AgentShutdownRequest;
import com.tracker.server.agent.dto.AgentSyncRequest;
import com.tracker.server.agent.entity.AgentDevice;
import com.tracker.server.agent.model.ActivityKind;
import com.tracker.server.agent.model.ActivityState;
import com.tracker.server.agent.repository.AgentActivityRepository;
import com.tracker.server.agent.repository.AgentDeviceRepository;
import com.tracker.server.agent.service.AgentDeviceService;
import com.tracker.server.agent.service.AgentRecordUpsertService;
import com.tracker.server.agent.service.AgentSyncService;
import com.tracker.server.dto.dashboard.ProcessActivityRow;
import com.tracker.server.entity.Device;
import com.tracker.server.entity.User;
import com.tracker.server.repository.ActiveWindowActivityRepository;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.repository.DeviceSessionRepository;
import com.tracker.server.repository.IdleActivityRepository;
import com.tracker.server.repository.ProcessActivityRepository;
import com.tracker.server.repository.UserRepository;
import com.tracker.server.service.AdminDashboardService;

@SpringBootTest
@ActiveProfiles("test")
class AgentSyncIntegrationTest {

    @Autowired AgentRecordUpsertService upsertService;
    @Autowired AgentSyncService syncService;
    @Autowired AgentDeviceService deviceService;
    @Autowired AdminDashboardService dashboardService;
    @Autowired AgentActivityRepository activityRepository;
    @Autowired AgentDeviceRepository agentDeviceRepository;
    @Autowired ProcessActivityRepository processRepository;
    @Autowired ActiveWindowActivityRepository windowRepository;
    @Autowired IdleActivityRepository idleRepository;
    @Autowired DeviceSessionRepository sessionRepository;
    @Autowired DeviceRepository deviceRepository;
    @Autowired UserRepository userRepository;

    private User user;
    private Device device;
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
                .role("USER")
                .build());
        device = deviceRepository.save(Device.builder()
                .macAddress("00-11-22-33-44-55")
                .machineName("test-machine")
                .osName("Windows")
                .lastSeen(LocalDateTime.now(ZoneOffset.UTC))
                .status("ACTIVE")
                .online(true)
                .user(user)
                .build());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        agentDevice = agentDeviceRepository.save(AgentDevice.builder()
                .deviceUuid(UUID.randomUUID().toString())
                .legacyDeviceId(device.getId())
                .userId(user.getId())
                .machineName("test-machine")
                .osName("Windows")
                .lifecycleState("ONLINE")
                .lastSeenAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Test
    void processRevisionUpdatesTheSameExactNaturalRecord() {
        String uuid = UUID.randomUUID().toString();
        Instant start = Instant.parse("2026-07-25T08:00:00Z");

        assertThat(upsertService.apply(agentDevice, user,
                snapshot(uuid, ActivityKind.PROCESS, 1, start, null,
                        ActivityState.OPEN, null, 4242L, "notepad.exe", null)).status())
                .isEqualTo("APPLIED");
        assertThat(upsertService.apply(agentDevice, user,
                snapshot(uuid, ActivityKind.PROCESS, 1, start, null,
                        ActivityState.OPEN, null, 4242L, "notepad.exe", null)).status())
                .isEqualTo("UNCHANGED");
        assertThat(upsertService.apply(agentDevice, user,
                snapshot(uuid, ActivityKind.PROCESS, 2, start, start.plusSeconds(10),
                        ActivityState.CLOSED, "PROCESS_EXIT", 4242L, "notepad.exe", null)).status())
                .isEqualTo("APPLIED");

        assertThat(processRepository.count()).isEqualTo(1);
        assertThat(processRepository.findAll().getFirst())
                .satisfies(row -> {
                    assertThat(row.getEndTime()).isEqualTo(utc(start.plusSeconds(10)));
                    assertThat(row.getDurationSeconds()).isEqualTo(10L);
                    assertThat(row.getStatus()).isEqualTo("CLOSED");
                });
    }

    @Test
    void differentUuidsWithSameExactProcessKeyShareOneProjection() {
        Instant start = Instant.parse("2026-07-25T08:10:00Z");
        var first = snapshot(UUID.randomUUID().toString(), ActivityKind.PROCESS, 1,
                start, null, ActivityState.OPEN, null, 11056L, "Taskmgr.exe", null);
        var duplicate = snapshot(UUID.randomUUID().toString(), ActivityKind.PROCESS, 1,
                start, null, ActivityState.OPEN, null, 11056L, "taskmgr.exe", null);

        upsertService.apply(agentDevice, user, first);
        upsertService.apply(agentDevice, user, duplicate);

        assertThat(activityRepository.count()).isEqualTo(2);
        assertThat(processRepository.count()).isEqualTo(1);
        Long projectionId = processRepository.findAll().getFirst().getId();
        assertThat(activityRepository.findById(first.recordUuid()).orElseThrow().getLegacyRecordId())
                .isEqualTo(projectionId);
        assertThat(activityRepository.findById(duplicate.recordUuid()).orElseThrow().getLegacyRecordId())
                .isEqualTo(projectionId);
    }

    @Test
    void aNewProcessStartClosesThePreviousRunningPid() {
        Instant firstStart = Instant.parse("2026-07-25T08:20:00Z");
        Instant secondStart = firstStart.plusSeconds(20);
        upsertService.apply(agentDevice, user, snapshot(
                UUID.randomUUID().toString(), ActivityKind.PROCESS, 1,
                firstStart, null, ActivityState.OPEN, null, 900L, "sample.exe", null));
        upsertService.apply(agentDevice, user, snapshot(
                UUID.randomUUID().toString(), ActivityKind.PROCESS, 1,
                secondStart, null, ActivityState.OPEN, null, 900L, "sample.exe", null));

        assertThat(processRepository.findByDeviceIdAndStatus(device.getId(), "RUNNING"))
                .singleElement()
                .satisfies(row -> assertThat(row.getStartTime()).isEqualTo(utc(secondStart)));
        assertThat(processRepository.findAll()).hasSize(2);
        assertThat(processRepository.findAll().stream()
                .filter(row -> row.getStartTime().equals(utc(firstStart)))
                .findFirst().orElseThrow().getEndTime()).isEqualTo(utc(secondStart));
    }

    @Test
    void openingAWindowClosesEveryPreviousRunningWindow() {
        Instant firstStart = Instant.parse("2026-07-25T08:30:00Z");
        Instant secondStart = firstStart.plusSeconds(8);
        upsertService.apply(agentDevice, user, snapshot(
                UUID.randomUUID().toString(), ActivityKind.ACTIVE_WINDOW, 1,
                firstStart, null, ActivityState.OPEN, null, 101L, "chrome.exe", "Inbox"));
        upsertService.apply(agentDevice, user, snapshot(
                UUID.randomUUID().toString(), ActivityKind.ACTIVE_WINDOW, 1,
                secondStart, null, ActivityState.OPEN, null, 101L, "chrome.exe", "Reports"));

        assertThat(windowRepository.findByDeviceIdAndStatus(device.getId(), "RUNNING"))
                .singleElement()
                .satisfies(row -> assertThat(row.getWindowTitle()).isEqualTo("Reports"));
        assertThat(windowRepository.findAll().stream()
                .filter(row -> "Inbox".equals(row.getWindowTitle()))
                .findFirst().orElseThrow().getEndTime()).isEqualTo(utc(secondStart));
    }

    @Test
    void openingIdleClosesEveryPreviousRunningIdle() {
        Instant firstStart = Instant.parse("2026-07-25T08:40:00Z");
        Instant secondStart = firstStart.plusSeconds(30);
        upsertService.apply(agentDevice, user, snapshot(
                UUID.randomUUID().toString(), ActivityKind.IDLE, 1,
                firstStart, null, ActivityState.OPEN, null, null, null, null));
        upsertService.apply(agentDevice, user, snapshot(
                UUID.randomUUID().toString(), ActivityKind.IDLE, 1,
                secondStart, null, ActivityState.OPEN, null, null, null, null));

        assertThat(idleRepository.findByDeviceIdAndStatus(device.getId(), "RUNNING"))
                .singleElement()
                .satisfies(row -> assertThat(row.getIdleStart()).isEqualTo(utc(secondStart)));
        assertThat(idleRepository.findAll().stream()
                .filter(row -> row.getIdleStart().equals(utc(firstStart)))
                .findFirst().orElseThrow().getIdleEnd()).isEqualTo(utc(secondStart));
    }

    @Test
    void offlineDisplayKeepsEndNullAndFreezesDurationAtLastSeen() {
        Instant start = Instant.parse("2026-07-25T09:00:00Z");
        upsertService.apply(agentDevice, user, snapshot(
                UUID.randomUUID().toString(), ActivityKind.PROCESS, 1,
                start, null, ActivityState.OPEN, null, 500L, "chrome.exe", null));
        device.setOnline(false);
        device.setStatus("OFFLINE");
        device.setLastSeen(utc(start.plusSeconds(12)));
        deviceRepository.save(device);

        var response = dashboardService.getActivities(
                device.getId(), "processes", "ALL", "UTC", null, null,
                null, null, 0, 20, "startTime", "desc", false);
        ProcessActivityRow row = (ProcessActivityRow) response.rows().getFirst();

        assertThat(row.status()).isEqualTo("OFFLINE");
        assertThat(row.endTime()).isNull();
        assertThat(row.durationSeconds()).isEqualTo(12L);
        assertThat(processRepository.findAll().getFirst().getEndTime()).isNull();
    }

    @Test
    void shutdownClosesAllRunningRowsEvenWhenDeviceWasOffline() {
        String sessionUuid = UUID.randomUUID().toString();
        Instant sessionStart = Instant.parse("2026-07-25T09:10:00Z");
        Instant shutdown = sessionStart.plusSeconds(60);
        agentDevice.setCurrentSessionUuid(sessionUuid);
        agentDevice.setCurrentSessionStartedAt(utc(sessionStart));
        agentDevice.setCurrentSessionSequence(1L);
        agentDevice.setLifecycleState("OFFLINE");
        agentDeviceRepository.save(agentDevice);
        device.setOnline(false);
        device.setStatus("OFFLINE");
        deviceRepository.save(device);

        upsertService.apply(agentDevice, user, snapshot(
                sessionUuid, ActivityKind.DEVICE_SESSION, 1,
                sessionStart, null, ActivityState.OPEN, null, null, null, null));
        upsertService.apply(agentDevice, user, snapshot(
                UUID.randomUUID().toString(), ActivityKind.PROCESS, 1,
                sessionStart.plusSeconds(2), null, ActivityState.OPEN,
                null, 777L, "work.exe", null));
        upsertService.apply(agentDevice, user, snapshot(
                UUID.randomUUID().toString(), ActivityKind.ACTIVE_WINDOW, 1,
                sessionStart.plusSeconds(3), null, ActivityState.OPEN,
                null, 777L, "work.exe", "Work"));
        upsertService.apply(agentDevice, user, snapshot(
                UUID.randomUUID().toString(), ActivityKind.IDLE, 1,
                sessionStart.plusSeconds(10), null, ActivityState.OPEN,
                null, null, null, null));

        deviceService.shutdown(
                user.getUsername(),
                agentDevice.getDeviceUuid(),
                new AgentShutdownRequest(
                        shutdown, sessionUuid, sessionStart, 1L, "SYSTEM_SHUTDOWN"),
                "127.0.0.1");

        assertThat(processRepository.findByDeviceIdAndStatus(device.getId(), "RUNNING")).isEmpty();
        assertThat(windowRepository.findByDeviceIdAndStatus(device.getId(), "RUNNING")).isEmpty();
        assertThat(idleRepository.findByDeviceIdAndStatus(device.getId(), "RUNNING")).isEmpty();
        assertThat(sessionRepository.findByDeviceIdAndStatus(device.getId(), "RUNNING")).isEmpty();
        assertThat(processRepository.findAll().getFirst().getEndTime()).isEqualTo(utc(shutdown));
        assertThat(windowRepository.findAll().getFirst().getEndTime()).isEqualTo(utc(shutdown));
        assertThat(idleRepository.findAll().getFirst().getIdleEnd()).isEqualTo(utc(shutdown));
        assertThat(sessionRepository.findAll().getFirst().getShutdownTime()).isEqualTo(utc(shutdown));
    }

    @Test
    void aLateOpenReplayCannotReopenAnAlreadyClosedNaturalRecord() {
        Instant start = Instant.parse("2026-07-25T09:30:00Z");
        String closedUuid = UUID.randomUUID().toString();
        upsertService.apply(agentDevice, user, snapshot(
                closedUuid, ActivityKind.PROCESS, 2,
                start, start.plusSeconds(5), ActivityState.CLOSED,
                "PROCESS_EXIT", 808L, "tool.exe", null));
        upsertService.apply(agentDevice, user, snapshot(
                UUID.randomUUID().toString(), ActivityKind.PROCESS, 1,
                start, null, ActivityState.OPEN, null, 808L, "tool.exe", null));

        assertThat(processRepository.count()).isEqualTo(1);
        assertThat(processRepository.findAll().getFirst().getStatus()).isEqualTo("CLOSED");
        assertThat(processRepository.findAll().getFirst().getEndTime())
                .isEqualTo(utc(start.plusSeconds(5)));
    }

    @Test
    void offlineDeviceBecomesOnlineOnlyAfterTheFinalBacklogBatch() {
        Instant start = Instant.parse("2026-07-25T09:35:00Z");
        String sessionUuid = UUID.randomUUID().toString();
        agentDevice.setLifecycleState("OFFLINE");
        agentDevice.setCurrentSessionUuid(null);
        agentDevice.setCurrentSessionStartedAt(null);
        agentDevice.setCurrentSessionSequence(null);
        agentDeviceRepository.save(agentDevice);
        device.setOnline(false);
        device.setStatus("OFFLINE");
        deviceRepository.save(device);

        syncService.synchronize(
                user.getUsername(),
                agentDevice.getDeviceUuid(),
                new AgentSyncRequest(
                        sessionUuid, start, 2L, Instant.now(), false,
                        List.of(snapshot(
                                UUID.randomUUID().toString(), ActivityKind.DEVICE_SESSION, 1,
                                start, null, ActivityState.OPEN, null, null, null, null))),
                "127.0.0.1");

        assertThat(agentDeviceRepository.findById(agentDevice.getId()).orElseThrow()
                .getLifecycleState()).isEqualTo("OFFLINE");
        assertThat(deviceRepository.findById(device.getId()).orElseThrow().isOnline()).isFalse();

        syncService.synchronize(
                user.getUsername(),
                agentDevice.getDeviceUuid(),
                new AgentSyncRequest(
                        sessionUuid, start, 2L, Instant.now(), true,
                        List.of(snapshot(
                                UUID.randomUUID().toString(), ActivityKind.PROCESS, 1,
                                start.plusSeconds(1), null, ActivityState.OPEN,
                                null, 501L, "work.exe", null))),
                "127.0.0.1");

        assertThat(agentDeviceRepository.findById(agentDevice.getId()).orElseThrow()
                .getLifecycleState()).isEqualTo("ONLINE");
        assertThat(deviceRepository.findById(device.getId()).orElseThrow().isOnline()).isTrue();
    }

    @Test
    void duplicateUuidInsideOneBatchIsRejectedWithoutCreatingAnotherRow() {
        Instant start = Instant.parse("2026-07-25T09:38:00Z");
        String recordUuid = UUID.randomUUID().toString();
        var record = snapshot(
                recordUuid, ActivityKind.PROCESS, 1,
                start, null, ActivityState.OPEN, null, 321L, "calc.exe", null);

        var response = syncService.synchronize(
                user.getUsername(),
                agentDevice.getDeviceUuid(),
                new AgentSyncRequest(
                        UUID.randomUUID().toString(), start.minusSeconds(1), 1L,
                        Instant.now(), true, List.of(record, record)),
                "127.0.0.1");

        assertThat(response.acknowledgements())
                .extracting(ack -> ack.status())
                .containsExactly("APPLIED", "REJECTED");
        assertThat(activityRepository.count()).isEqualTo(1);
        assertThat(processRepository.count()).isEqualTo(1);
    }

    @Test
    void anInvalidRecordDoesNotBlockAValidRecordInTheSameBatch() {
        Instant start = Instant.parse("2026-07-25T09:40:00Z");
        var invalid = snapshot(
                UUID.randomUUID().toString(), ActivityKind.IDLE, 2,
                start, null, ActivityState.CLOSED, "USER_ACTIVE", null, null, null);
        var valid = snapshot(
                UUID.randomUUID().toString(), ActivityKind.PROCESS, 1,
                start, null, ActivityState.OPEN, null, 123L, "notepad.exe", null);
        String sessionUuid = UUID.randomUUID().toString();

        var response = syncService.synchronize(
                user.getUsername(),
                agentDevice.getDeviceUuid(),
                new AgentSyncRequest(
                        sessionUuid, start.minusSeconds(1), 1L, Instant.now(), true,
                        List.of(invalid, valid)),
                "127.0.0.1");

        assertThat(response.acknowledgements())
                .extracting(ack -> ack.status())
                .containsExactly("REJECTED", "APPLIED");
        assertThat(activityRepository.findById(valid.recordUuid())).isPresent();
    }

    private static ActivitySnapshotRequest snapshot(
            String uuid,
            ActivityKind kind,
            long revision,
            Instant start,
            Instant end,
            ActivityState state,
            String reason,
            Long pid,
            String processName,
            String title) {
        return new ActivitySnapshotRequest(
                uuid, kind, revision, start, end, state, reason,
                pid, processName, title, null);
    }

    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
