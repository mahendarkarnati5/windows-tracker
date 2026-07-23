package com.tracker.server.agent.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
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
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ActivityAcknowledgement apply(
            AgentDevice agentDevice,
            User user,
            ActivitySnapshotRequest request) {

        String recordUuid = canonicalUuid(request.recordUuid());
        validate(request);

        // A concurrent first insert can race because no row exists to lock. The primary
        // key resolves that race; retrying in a fresh transaction then locks the winner.
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return transactionTemplate.execute(status ->
                        applyInTransaction(agentDevice, user, request, recordUuid));
            } catch (DataIntegrityViolationException ex) {
                if (attempt == 1) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("Unable to apply activity record");
    }

    private ActivityAcknowledgement applyInTransaction(
            AgentDevice agentDevice,
            User user,
            ActivitySnapshotRequest request,
            String recordUuid) {

        // Lock order is always device first, then canonical activity. The duplicate-repair
        // service follows the same order, preventing a periodic repair from deadlocking
        // with a live agent sync. It also serializes different record UUIDs that describe
        // the same process lifecycle on one device.
        Device device = deviceRepository.findByIdForUpdate(agentDevice.getLegacyDeviceId())
                .orElseThrow(() -> new IllegalStateException("Mapped device no longer exists"));
        AgentActivity current = activityRepository.findByRecordUuidForUpdate(recordUuid).orElse(null);

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
            current = activityRepository.saveAndFlush(current);
            project(current, device, user);
            activityRepository.save(current);
            return acknowledgement(current, "APPLIED");
        }

        assertOwnershipAndKind(current, agentDevice, user, request.kind());

        if (current.getRevision() > request.revision()) {
            return acknowledgement(current, "STALE");
        }
        if (current.getRevision() == request.revision()) {
            // The legacy projection may have been temporarily closed by the heartbeat
            // timeout. Re-project the unchanged authoritative snapshot so a reconnect can
            // restore a genuinely open record without inventing a new revision.
            project(current, device, user);
            activityRepository.save(current);
            return acknowledgement(current, "UNCHANGED");
        }

        copySnapshot(current, request);
        project(current, device, user);
        activityRepository.save(current);
        return acknowledgement(current, "APPLIED");
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
        ProcessActivity target = existingProcessTarget(source, device);
        target.setPid(source.getProcessId());
        target.setProcessName(source.getProcessName());
        if (target.getStartTime() == null || source.getStartedAt().isBefore(target.getStartTime())) {
            target.setStartTime(source.getStartedAt());
        }
        target.setEndTime(source.getEndedAt());
        target.setDurationSeconds(seconds(source.getDurationMillis()));
        target.setStatus(legacyStatus(source));
        target.setDevice(device);
        target.setUser(user);
        return processRepository.saveAndFlush(target).getId();
    }

    private ProcessActivity existingProcessTarget(AgentActivity source, Device device) {
        ProcessActivity mapped = mappedProcess(source, device);
        if (mapped != null) {
            return mapped;
        }
        if (source.getProcessId() != null && source.getStartedAt() != null) {
            var naturalMatches = processRepository.findByDeviceIdAndPidAndStartTimeOrderByIdDesc(
                    device.getId(), source.getProcessId(), source.getStartedAt());
            if (!naturalMatches.isEmpty()) {
                return naturalMatches.getFirst();
            }

            var runningForPid = processRepository
                    .findByDeviceIdAndPidAndStatusOrderByStartTimeAscIdAsc(
                            device.getId(), source.getProcessId(), "RUNNING");
            for (ProcessActivity running : runningForPid) {
                boolean sameName = running.getProcessName() != null
                        && source.getProcessName() != null
                        && running.getProcessName().equalsIgnoreCase(source.getProcessName());
                long startDifference = running.getStartTime() == null
                        ? Long.MAX_VALUE
                        : Math.abs(Duration.between(
                                running.getStartTime(), source.getStartedAt()).toSeconds());
                if (sameName && startDifference <= 10L) {
                    return running;
                }

                LocalDateTime inferredEnd = running.getStartTime() != null
                                && !source.getStartedAt().isBefore(running.getStartTime())
                        ? source.getStartedAt()
                        : null;
                running.setEndTime(inferredEnd);
                running.setDurationSeconds(inferredEnd == null || running.getStartTime() == null
                        ? null
                        : Math.max(0L, Duration.between(
                                running.getStartTime(), inferredEnd).toSeconds()));
                running.setStatus("INTERRUPTED");
                processRepository.save(running);
            }
        }
        return new ProcessActivity();
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
        ActiveWindowActivity target = existingWindowTarget(source, device);
        target.setWindowTitle(source.getWindowTitle());
        target.setStartTime(source.getStartedAt());
        target.setEndTime(source.getEndedAt());
        target.setDurationSeconds(seconds(source.getDurationMillis()));
        target.setStatus(legacyStatus(source));
        if (target.getOfflineId() == null || target.getOfflineId().isBlank()) {
            target.setOfflineId(source.getRecordUuid());
        }
        target.setDevice(device);
        return windowRepository.saveAndFlush(target).getId();
    }

    private ActiveWindowActivity existingWindowTarget(AgentActivity source, Device device) {
        if (source.getLegacyRecordId() != null) {
            ActiveWindowActivity mapped = windowRepository.findById(source.getLegacyRecordId())
                    .filter(row -> row.getDevice() != null
                            && device.getId().equals(row.getDevice().getId()))
                    .orElse(null);
            if (mapped != null) {
                return mapped;
            }
        }
        ActiveWindowActivity byUuid = windowRepository.findByOfflineId(source.getRecordUuid())
                .filter(row -> row.getDevice() != null
                        && device.getId().equals(row.getDevice().getId()))
                .orElse(null);
        if (byUuid != null) {
            return byUuid;
        }
        if (source.getStartedAt() != null && source.getWindowTitle() != null) {
            var naturalMatches = windowRepository
                    .findByDeviceIdAndStartTimeAndWindowTitleOrderByIdDesc(
                            device.getId(), source.getStartedAt(), source.getWindowTitle());
            if (!naturalMatches.isEmpty()) {
                return naturalMatches.getFirst();
            }
        }
        return new ActiveWindowActivity();
    }

    private Long projectIdle(AgentActivity source, Device device, User user) {
        IdleActivity target = existingIdleTarget(source, device);
        target.setIdleStart(source.getStartedAt());
        target.setIdleEnd(source.getEndedAt());
        target.setIdleSeconds(seconds(source.getDurationMillis()));
        target.setStatus(legacyStatus(source));
        target.setDevice(device);
        target.setUser(user);
        return idleRepository.saveAndFlush(target).getId();
    }

    private IdleActivity existingIdleTarget(AgentActivity source, Device device) {
        if (source.getLegacyRecordId() != null) {
            IdleActivity mapped = idleRepository.findById(source.getLegacyRecordId())
                    .filter(row -> row.getDevice() != null
                            && device.getId().equals(row.getDevice().getId()))
                    .orElse(null);
            if (mapped != null) {
                return mapped;
            }
        }
        if (source.getStartedAt() != null) {
            var naturalMatches = idleRepository.findByDeviceIdAndIdleStartOrderByIdDesc(
                    device.getId(), source.getStartedAt());
            if (!naturalMatches.isEmpty()) {
                return naturalMatches.getFirst();
            }
        }
        return new IdleActivity();
    }

    private Long projectSession(AgentActivity source, Device device, User user) {
        DeviceSession target = existingSessionTarget(source, device);
        target.setStartupTime(source.getStartedAt());
        target.setShutdownTime(source.getEndedAt());
        target.setSessionDurationSeconds(seconds(source.getDurationMillis()));
        target.setStatus(legacyStatus(source));
        target.setDevice(device);
        target.setUser(user);
        return sessionRepository.saveAndFlush(target).getId();
    }

    private DeviceSession existingSessionTarget(AgentActivity source, Device device) {
        if (source.getLegacyRecordId() != null) {
            DeviceSession mapped = sessionRepository.findById(source.getLegacyRecordId())
                    .filter(row -> row.getDevice() != null
                            && device.getId().equals(row.getDevice().getId()))
                    .orElse(null);
            if (mapped != null) {
                return mapped;
            }
        }
        if (source.getStartedAt() != null) {
            var naturalMatches = sessionRepository.findByDeviceIdAndStartupTimeOrderByIdDesc(
                    device.getId(), source.getStartedAt());
            if (!naturalMatches.isEmpty()) {
                return naturalMatches.getFirst();
            }
        }
        return new DeviceSession();
    }

    private static void validate(ActivitySnapshotRequest request) {
        if (request.state() == ActivityState.OPEN && request.endedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OPEN records cannot have an end time");
        }
        if (request.state() != ActivityState.OPEN && request.endedAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Closed records require an end time");
        }
        if (request.endedAt() != null && request.endedAt().isBefore(request.startedAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time is before start time");
        }
        if ((request.kind() == ActivityKind.PROCESS || request.kind() == ActivityKind.ACTIVE_WINDOW)
                && (request.processName() == null || request.processName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Process name is required");
        }
        if (request.kind() == ActivityKind.ACTIVE_WINDOW
                && (request.windowTitle() == null || request.windowTitle().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Window title is required");
        }
    }

    private static ActivityAcknowledgement acknowledgement(AgentActivity activity, String status) {
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
        return durationMillis == null ? null : durationMillis / 1000L;
    }

    private static String legacyStatus(AgentActivity activity) {
        if (activity.getState() == ActivityState.OPEN) {
            return "RUNNING";
        }
        if (activity.getState() == ActivityState.INFERRED) {
            return "INTERRUPTED";
        }
        if (activity.getKind() == ActivityKind.DEVICE_SESSION) {
            String reason = activity.getCloseReason() == null
                    ? ""
                    : activity.getCloseReason().trim().toUpperCase();
            return switch (reason) {
                case "SYSTEM_SHUTDOWN", "SYSTEM_RESTART" -> "SHUTDOWN";
                case "USER_LOGOFF" -> "LOGOFF";
                case "AGENT_STOP" -> "STOPPED";
                default -> "CLOSED";
            };
        }
        return "CLOSED";
    }
}
