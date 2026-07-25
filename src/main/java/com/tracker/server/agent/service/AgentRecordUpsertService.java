package com.tracker.server.agent.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.tracker.server.agent.dto.ActivityAcknowledgement;
import com.tracker.server.agent.dto.ActivitySnapshotRequest;
import com.tracker.server.agent.entity.AgentActivity;
import com.tracker.server.agent.entity.AgentDevice;
import com.tracker.server.agent.model.ActivityKind;
import com.tracker.server.agent.model.ActivityState;
import com.tracker.server.agent.repository.AgentActivityRepository;
import com.tracker.server.agent.util.ActivityNaturalKey;
import com.tracker.server.agent.util.AgentTextLimits;
import com.tracker.server.entity.ActiveWindowActivity;
import com.tracker.server.entity.Device;
import com.tracker.server.entity.DeviceSession;
import com.tracker.server.entity.IdleActivity;
import com.tracker.server.entity.ProcessActivity;
import com.tracker.server.entity.User;
import com.tracker.server.repository.ActiveWindowActivityRepository;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.repository.DeviceSessionRepository;
import com.tracker.server.repository.IdleActivityRepository;
import com.tracker.server.repository.ProcessActivityRepository;

@Service
public class AgentRecordUpsertService {

    private final AgentActivityRepository activityRepository;
    private final DeviceRepository deviceRepository;
    private final ProcessActivityRepository processRepository;
    private final ActiveWindowActivityRepository windowRepository;
    private final IdleActivityRepository idleRepository;
    private final DeviceSessionRepository sessionRepository;
    private final TransactionTemplate transactionTemplate;

    public AgentRecordUpsertService(
            AgentActivityRepository activityRepository,
            DeviceRepository deviceRepository,
            ProcessActivityRepository processRepository,
            ActiveWindowActivityRepository windowRepository,
            IdleActivityRepository idleRepository,
            DeviceSessionRepository sessionRepository,
            PlatformTransactionManager transactionManager) {
        this.activityRepository = activityRepository;
        this.deviceRepository = deviceRepository;
        this.processRepository = processRepository;
        this.windowRepository = windowRepository;
        this.idleRepository = idleRepository;
        this.sessionRepository = sessionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    public ActivityAcknowledgement apply(
            AgentDevice agentDevice,
            User user,
            ActivitySnapshotRequest request) {
        String recordUuid = canonicalUuid(request.recordUuid());
        validate(request);
        return transactionTemplate.execute(status ->
                applyInTransaction(
                        agentDevice,
                        user,
                        requireLegacyDevice(agentDevice),
                        request,
                        recordUuid,
                        activityRepository.findByRecordUuidForUpdate(recordUuid).orElse(null)));
    }

    ActivityAcknowledgement applyInBatch(
            AgentDevice agentDevice,
            User user,
            Device device,
            ActivitySnapshotRequest request,
            AgentActivity current) {
        String recordUuid = canonicalUuid(request.recordUuid());
        validate(request);
        return applyInTransaction(
                agentDevice, user, device, request, recordUuid, current);
    }

    Device requireLegacyDevice(AgentDevice agentDevice) {
        return deviceRepository.findById(agentDevice.getLegacyDeviceId())
                .orElseThrow(() -> new IllegalStateException("Mapped device no longer exists"));
    }

    private ActivityAcknowledgement applyInTransaction(
            AgentDevice agentDevice,
            User user,
            Device device,
            ActivitySnapshotRequest request,
            String recordUuid,
            AgentActivity current) {
        if (current == null) {
            current = AgentActivity.builder()
                    .recordUuid(recordUuid)
                    .deviceUuid(agentDevice.getDeviceUuid())
                    .userId(user.getId())
                    .kind(request.kind())
                    .legacyRecordId(validateLegacyRecordId(
                            request.kind(), request.legacyRecordId(), agentDevice.getLegacyDeviceId()))
                    .createdAt(utc(Instant.now()))
                    .build();
            copySnapshot(current, request);
            project(current, device, user);
            activityRepository.save(current);
            return acknowledgement(current, "APPLIED");
        }

        assertOwnershipAndKind(current, agentDevice, user, request.kind());
        if (current.getRevision() > request.revision()) {
            return acknowledgement(current, "STALE");
        }
        boolean changed = current.getRevision() < request.revision();
        if (changed) {
            copySnapshot(current, request);
        }

        // Re-project equal revisions as well. This safely repairs a projection after a
        // reconnect without creating a second activity row.
        project(current, device, user);
        activityRepository.save(current);
        return acknowledgement(current, changed ? "APPLIED" : "UNCHANGED");
    }

    private static void copySnapshot(AgentActivity target, ActivitySnapshotRequest source) {
        LocalDateTime end = source.endedAt() == null ? null : utc(source.endedAt());
        target.setRevision(source.revision());
        target.setStartedAt(utc(source.startedAt()));
        target.setEndedAt(end);
        target.setDurationMillis(source.endedAt() == null
                ? null
                : Duration.between(source.startedAt(), source.endedAt()).toMillis());
        target.setState(source.state());
        target.setCloseReason(source.closeReason());
        target.setProcessId(source.processId());
        target.setProcessName(AgentTextLimits.processName(source.processName()));
        target.setWindowTitle(AgentTextLimits.windowTitle(source.windowTitle()));
        target.setUpdatedAt(utc(Instant.now()));
    }

    private void assertOwnershipAndKind(
            AgentActivity current,
            AgentDevice device,
            User user,
            ActivityKind incomingKind) {
        if (!current.getDeviceUuid().equals(device.getDeviceUuid())
                || !current.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Record UUID belongs to another device or user");
        }
        if (current.getKind() != incomingKind) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Record kind cannot change between revisions");
        }
    }

    private void project(AgentActivity activity, Device device, User user) {
        Long legacyId = switch (activity.getKind()) {
            case PROCESS -> projectProcess(activity, device, user);
            case ACTIVE_WINDOW -> projectWindow(activity, device);
            case IDLE -> projectIdle(activity, device, user);
            case DEVICE_SESSION -> projectSession(activity, device, user);
        };
        activity.setLegacyRecordId(legacyId);
    }

    private Long validateLegacyRecordId(ActivityKind kind, Long legacyId, Long deviceId) {
        if (legacyId == null) {
            return null;
        }
        boolean owned = switch (kind) {
            case PROCESS -> processRepository.findById(legacyId)
                    .map(record -> record.getDevice() != null
                            && deviceId.equals(record.getDevice().getId()))
                    .orElse(false);
            case ACTIVE_WINDOW -> windowRepository.findById(legacyId)
                    .map(record -> record.getDevice() != null
                            && deviceId.equals(record.getDevice().getId()))
                    .orElse(false);
            case IDLE -> idleRepository.findById(legacyId)
                    .map(record -> record.getDevice() != null
                            && deviceId.equals(record.getDevice().getId()))
                    .orElse(false);
            case DEVICE_SESSION -> sessionRepository.findById(legacyId)
                    .map(record -> record.getDevice() != null
                            && deviceId.equals(record.getDevice().getId()))
                    .orElse(false);
        };
        if (!owned) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Legacy record does not belong to the enrolled device");
        }
        return legacyId;
    }

    private Long projectProcess(AgentActivity source, Device device, User user) {
        String naturalKey = ActivityNaturalKey.of(source);
        ProcessActivity target = mappedProcess(source, device);
        if (target == null) {
            target = processRepository
                    .findFirstByDeviceIdAndPidAndProcessNameIgnoreCaseAndStartTimeAndEndTimeIsNullOrderByIdDesc(
                            device.getId(),
                            source.getProcessId(),
                            source.getProcessName(),
                            source.getStartedAt())
                    .orElse(null);
        }
        if (target == null) {
            target = processRepository
                    .findFirstByDeviceIdAndPidAndProcessNameIgnoreCaseAndStartTimeOrderByIdDesc(
                            device.getId(),
                            source.getProcessId(),
                            source.getProcessName(),
                            source.getStartedAt())
                    .orElse(null);
        }
        if (target == null) {
            target = processRepository
                    .findFirstByDeviceIdAndNaturalKeyOrderByIdDesc(device.getId(), naturalKey)
                    .orElseGet(ProcessActivity::new);
        }

        if (source.getState() == ActivityState.OPEN
                && target.getId() != null
                && target.getEndTime() != null) {
            return target.getId();
        }

        boolean newProjection = target.getId() == null;
        if (source.getState() == ActivityState.OPEN && newProjection) {
            closeOtherRunningProcesses(
                    device.getId(), source.getProcessId(), null, source.getStartedAt());
        }

        target.setPid(source.getProcessId());
        target.setProcessName(source.getProcessName());
        target.setStartTime(source.getStartedAt());
        target.setEndTime(source.getEndedAt());
        target.setDurationSeconds(seconds(source.getDurationMillis()));
        target.setStatus(legacyStatus(source));
        target.setNaturalKey(naturalKey);
        target.setDevice(device);
        target.setUser(user);
        return processRepository.save(target).getId();
    }

    private ProcessActivity mappedProcess(AgentActivity source, Device device) {
        if (source.getLegacyRecordId() == null) {
            return null;
        }
        return processRepository.findById(source.getLegacyRecordId())
                .filter(row -> row.getDevice() != null
                        && device.getId().equals(row.getDevice().getId()))
                .orElse(null);
    }

    private Long projectWindow(AgentActivity source, Device device) {
        String naturalKey = ActivityNaturalKey.of(source);
        ActiveWindowActivity target = mappedWindow(source, device);
        if (target == null) {
            target = windowRepository
                    .findFirstByDeviceIdAndPidAndProcessNameIgnoreCaseAndWindowTitleAndStartTimeAndEndTimeIsNullOrderByIdDesc(
                            device.getId(),
                            source.getProcessId(),
                            source.getProcessName(),
                            source.getWindowTitle(),
                            source.getStartedAt())
                    .orElse(null);
        }
        if (target == null) {
            target = windowRepository
                    .findFirstByDeviceIdAndPidAndProcessNameIgnoreCaseAndWindowTitleAndStartTimeOrderByIdDesc(
                            device.getId(),
                            source.getProcessId(),
                            source.getProcessName(),
                            source.getWindowTitle(),
                            source.getStartedAt())
                    .orElse(null);
        }
        if (target == null) {
            target = windowRepository
                    .findByDeviceIdAndStartTimeAndWindowTitleOrderByIdDesc(
                            device.getId(), source.getStartedAt(), source.getWindowTitle())
                    .stream().findFirst().orElse(null);
        }
        if (target == null) {
            target = windowRepository
                    .findFirstByDeviceIdAndNaturalKeyOrderByIdDesc(device.getId(), naturalKey)
                    .orElseGet(ActiveWindowActivity::new);
        }

        if (source.getState() == ActivityState.OPEN
                && target.getId() != null
                && target.getEndTime() != null) {
            return target.getId();
        }
        if (source.getState() == ActivityState.OPEN) {
            closeOtherRunningWindows(device.getId(), target.getId(), source.getStartedAt());
        }

        target.setPid(source.getProcessId());
        target.setProcessName(source.getProcessName());
        target.setWindowTitle(source.getWindowTitle());
        target.setStartTime(source.getStartedAt());
        target.setEndTime(source.getEndedAt());
        target.setDurationSeconds(seconds(source.getDurationMillis()));
        target.setStatus(legacyStatus(source));
        target.setNaturalKey(naturalKey);
        if (target.getOfflineId() == null || target.getOfflineId().isBlank()) {
            target.setOfflineId(source.getRecordUuid());
        }
        target.setDevice(device);
        return windowRepository.save(target).getId();
    }

    private ActiveWindowActivity mappedWindow(AgentActivity source, Device device) {
        if (source.getLegacyRecordId() != null) {
            ActiveWindowActivity mapped = windowRepository.findById(source.getLegacyRecordId())
                    .filter(row -> row.getDevice() != null
                            && device.getId().equals(row.getDevice().getId()))
                    .orElse(null);
            if (mapped != null) {
                return mapped;
            }
        }
        return windowRepository.findByOfflineId(source.getRecordUuid())
                .filter(row -> row.getDevice() != null
                        && device.getId().equals(row.getDevice().getId()))
                .orElse(null);
    }

    private Long projectIdle(AgentActivity source, Device device, User user) {
        String naturalKey = ActivityNaturalKey.of(source);
        IdleActivity target = mappedIdle(source, device);
        if (target == null) {
            target = idleRepository
                    .findFirstByDeviceIdAndIdleStartAndIdleEndIsNullOrderByIdDesc(
                            device.getId(), source.getStartedAt())
                    .orElse(null);
        }
        if (target == null) {
            target = idleRepository
                    .findByDeviceIdAndIdleStartOrderByIdDesc(device.getId(), source.getStartedAt())
                    .stream().findFirst().orElse(null);
        }
        if (target == null) {
            target = idleRepository
                    .findFirstByDeviceIdAndNaturalKeyOrderByIdDesc(device.getId(), naturalKey)
                    .orElseGet(IdleActivity::new);
        }

        if (source.getState() == ActivityState.OPEN
                && target.getId() != null
                && target.getIdleEnd() != null) {
            return target.getId();
        }
        if (source.getState() == ActivityState.OPEN) {
            closeOtherRunningIdle(device.getId(), target.getId(), source.getStartedAt());
        }

        target.setIdleStart(source.getStartedAt());
        target.setIdleEnd(source.getEndedAt());
        target.setIdleSeconds(seconds(source.getDurationMillis()));
        target.setStatus(legacyStatus(source));
        target.setNaturalKey(naturalKey);
        target.setDevice(device);
        target.setUser(user);
        return idleRepository.save(target).getId();
    }

    private IdleActivity mappedIdle(AgentActivity source, Device device) {
        if (source.getLegacyRecordId() == null) {
            return null;
        }
        return idleRepository.findById(source.getLegacyRecordId())
                .filter(row -> row.getDevice() != null
                        && device.getId().equals(row.getDevice().getId()))
                .orElse(null);
    }

    private Long projectSession(AgentActivity source, Device device, User user) {
        String naturalKey = ActivityNaturalKey.of(source);
        DeviceSession target = mappedSession(source, device);
        if (target == null) {
            target = sessionRepository
                    .findFirstByDeviceIdAndStartupTimeAndShutdownTimeIsNullOrderByIdDesc(
                            device.getId(), source.getStartedAt())
                    .orElse(null);
        }
        if (target == null) {
            target = sessionRepository
                    .findByDeviceIdAndStartupTimeOrderByIdDesc(device.getId(), source.getStartedAt())
                    .stream().findFirst().orElse(null);
        }
        if (target == null) {
            target = sessionRepository
                    .findFirstByDeviceIdAndNaturalKeyOrderByIdDesc(device.getId(), naturalKey)
                    .orElseGet(DeviceSession::new);
        }

        if (source.getState() == ActivityState.OPEN
                && target.getId() != null
                && target.getShutdownTime() != null) {
            return target.getId();
        }
        boolean newProjection = target.getId() == null;
        if (source.getState() == ActivityState.OPEN && newProjection) {
            closePreviousRunningAtRestart(
                    device.getId(), null, source.getStartedAt());
        }

        target.setStartupTime(source.getStartedAt());
        target.setShutdownTime(source.getEndedAt());
        target.setSessionDurationSeconds(seconds(source.getDurationMillis()));
        target.setStatus(legacyStatus(source));
        target.setNaturalKey(naturalKey);
        target.setDevice(device);
        target.setUser(user);
        return sessionRepository.save(target).getId();
    }

    private DeviceSession mappedSession(AgentActivity source, Device device) {
        if (source.getLegacyRecordId() == null) {
            return null;
        }
        return sessionRepository.findById(source.getLegacyRecordId())
                .filter(row -> row.getDevice() != null
                        && device.getId().equals(row.getDevice().getId()))
                .orElse(null);
    }

    private void closeOtherRunningProcesses(
            Long deviceId, Long pid, Long keepId, LocalDateTime newStart) {
        for (ProcessActivity row : processRepository
                .findByDeviceIdAndPidAndStatusOrderByStartTimeAscIdAsc(
                        deviceId, pid, "RUNNING")) {
            if (Objects.equals(row.getId(), keepId)) {
                continue;
            }
            closeProcess(row, newStart, "CLOSED");
        }
    }

    private void closeOtherRunningWindows(
            Long deviceId, Long keepId, LocalDateTime newStart) {
        for (ActiveWindowActivity row : windowRepository.findByDeviceIdAndStatus(deviceId, "RUNNING")) {
            if (Objects.equals(row.getId(), keepId)) {
                continue;
            }
            closeWindow(row, newStart, "CLOSED");
        }
    }

    private void closeOtherRunningIdle(Long deviceId, Long keepId, LocalDateTime newStart) {
        for (IdleActivity row : idleRepository.findByDeviceIdAndStatus(deviceId, "RUNNING")) {
            if (Objects.equals(row.getId(), keepId)) {
                continue;
            }
            closeIdle(row, newStart, "CLOSED");
        }
    }

    private void closePreviousRunningAtRestart(
            Long deviceId, Long keepSessionId, LocalDateTime restartAt) {
        for (ProcessActivity row : processRepository.findByDeviceIdAndStatus(deviceId, "RUNNING")) {
            closeProcess(row, restartAt, "CLOSED");
        }
        for (ActiveWindowActivity row : windowRepository.findByDeviceIdAndStatus(deviceId, "RUNNING")) {
            closeWindow(row, restartAt, "CLOSED");
        }
        for (IdleActivity row : idleRepository.findByDeviceIdAndStatus(deviceId, "RUNNING")) {
            closeIdle(row, restartAt, "CLOSED");
        }
        for (DeviceSession row : sessionRepository.findByDeviceIdAndStatus(deviceId, "RUNNING")) {
            if (Objects.equals(row.getId(), keepSessionId)) {
                continue;
            }
            closeSession(row, restartAt, "SHUTDOWN");
        }
    }

    private void closeProcess(ProcessActivity row, LocalDateTime end, String status) {
        LocalDateTime safeEnd = safeEnd(row.getStartTime(), end);
        row.setEndTime(safeEnd);
        row.setDurationSeconds(durationSeconds(row.getStartTime(), safeEnd));
        row.setStatus(status);
        processRepository.save(row);
    }

    private void closeWindow(ActiveWindowActivity row, LocalDateTime end, String status) {
        LocalDateTime safeEnd = safeEnd(row.getStartTime(), end);
        row.setEndTime(safeEnd);
        row.setDurationSeconds(durationSeconds(row.getStartTime(), safeEnd));
        row.setStatus(status);
        windowRepository.save(row);
    }

    private void closeIdle(IdleActivity row, LocalDateTime end, String status) {
        LocalDateTime safeEnd = safeEnd(row.getIdleStart(), end);
        row.setIdleEnd(safeEnd);
        row.setIdleSeconds(durationSeconds(row.getIdleStart(), safeEnd));
        row.setStatus(status);
        idleRepository.save(row);
    }

    private void closeSession(DeviceSession row, LocalDateTime end, String status) {
        LocalDateTime safeEnd = safeEnd(row.getStartupTime(), end);
        row.setShutdownTime(safeEnd);
        row.setSessionDurationSeconds(durationSeconds(row.getStartupTime(), safeEnd));
        row.setStatus(status);
        sessionRepository.save(row);
    }

    private static void validate(ActivitySnapshotRequest request) {
        if (request.state() == ActivityState.OPEN && request.endedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "OPEN records cannot have an end time");
        }
        if (request.state() != ActivityState.OPEN && request.endedAt() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Closed records require an end time");
        }
        if (request.endedAt() != null && request.endedAt().isBefore(request.startedAt())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "End time is before start time");
        }
        if ((request.kind() == ActivityKind.PROCESS
                || request.kind() == ActivityKind.ACTIVE_WINDOW)
                && (request.processId() == null
                    || request.processName() == null
                    || request.processName().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "PID and process name are required");
        }
        if (request.kind() == ActivityKind.ACTIVE_WINDOW
                && (request.windowTitle() == null || request.windowTitle().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Window title is required");
        }
    }

    private static ActivityAcknowledgement acknowledgement(
            AgentActivity activity, String status) {
        return new ActivityAcknowledgement(
                activity.getRecordUuid(), activity.getRevision(), status, null);
    }

    private static String canonicalUuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid record UUID", ex);
        }
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Long seconds(Long durationMillis) {
        return durationMillis == null ? null : Math.max(0L, durationMillis / 1000L);
    }

    private static String legacyStatus(AgentActivity activity) {
        if (activity.getState() == ActivityState.OPEN) {
            return "RUNNING";
        }
        if (activity.getKind() == ActivityKind.DEVICE_SESSION) {
            String reason = activity.getCloseReason() == null
                    ? ""
                    : activity.getCloseReason().trim().toUpperCase();
            return switch (reason) {
                case "SYSTEM_SHUTDOWN", "SYSTEM_RESTART", "CRASH_RECOVERY" -> "SHUTDOWN";
                case "USER_LOGOFF" -> "LOGOFF";
                case "AGENT_STOP" -> "STOPPED";
                default -> "CLOSED";
            };
        }
        // Inferred recovery is a closed record with an inferred close reason. It is not
        // a separate online/offline status and must not appear as INTERRUPTED.
        return "CLOSED";
    }

    private static LocalDateTime safeEnd(LocalDateTime start, LocalDateTime requestedEnd) {
        if (requestedEnd == null) {
            return null;
        }
        return start != null && requestedEnd.isBefore(start) ? start : requestedEnd;
    }

    private static Long durationSeconds(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return Math.max(0L, Duration.between(start, end).toSeconds());
    }
}
