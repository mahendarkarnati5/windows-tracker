package com.tracker.server.agent.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tracker.server.agent.entity.AgentActivity;
import com.tracker.server.agent.entity.AgentDevice;
import com.tracker.server.agent.model.ActivityKind;
import com.tracker.server.agent.model.ActivityState;
import com.tracker.server.agent.repository.AgentActivityRepository;
import com.tracker.server.entity.ActiveWindowActivity;
import com.tracker.server.entity.DeviceSession;
import com.tracker.server.entity.IdleActivity;
import com.tracker.server.entity.ProcessActivity;
import com.tracker.server.repository.ActiveWindowActivityRepository;
import com.tracker.server.repository.DeviceSessionRepository;
import com.tracker.server.repository.IdleActivityRepository;
import com.tracker.server.repository.ProcessActivityRepository;

import lombok.RequiredArgsConstructor;

/**
 * Maintains the dashboard/legacy projection without changing the authoritative
 * revisioned agent_activities rows.
 *
 * <p>Heartbeat timeouts are therefore reversible. A timeout can temporarily
 * close a RUNNING row at the last heartbeat, while a later agent revision still
 * replaces that temporary end time with the locally observed end time.</p>
 */
@Service
@RequiredArgsConstructor
public class AgentProjectionService {

    private final AgentActivityRepository activityRepository;
    private final ProcessActivityRepository processRepository;
    private final ActiveWindowActivityRepository windowRepository;
    private final IdleActivityRepository idleRepository;
    private final DeviceSessionRepository sessionRepository;

    @Transactional
    public void temporarilyCloseForOffline(AgentDevice device, LocalDateTime lastSeenAt) {
        LocalDateTime fallback = lastSeenAt == null ? LocalDateTime.now(ZoneOffset.UTC) : lastSeenAt;
        for (AgentActivity activity : openActivities(device)) {
            temporarilyClose(activity, fallback, false);
        }
        temporarilyCloseLegacyRunning(device.getLegacyDeviceId(), fallback, false);
    }

    @Transactional
    public void temporarilyCloseForShutdown(AgentDevice device, LocalDateTime shutdownAt) {
        for (AgentActivity activity : openActivities(device)) {
            temporarilyClose(activity, shutdownAt, true);
        }
        temporarilyCloseLegacyRunning(device.getLegacyDeviceId(), shutdownAt, true);
    }

    @Transactional
    public void temporarilyCloseLegacyOnly(
            Long legacyDeviceId, LocalDateTime requestedEnd, boolean explicitShutdown) {
        temporarilyCloseLegacyRunning(legacyDeviceId, requestedEnd, explicitShutdown);
    }

    @Transactional
    public void reconcileOpenRecords(AgentDevice device, Set<String> agentOpenRecordUuids) {
        Set<String> open = agentOpenRecordUuids == null
                ? Set.of()
                : new HashSet<>(agentOpenRecordUuids);
        for (AgentActivity activity : openActivities(device)) {
            if (open.contains(activity.getRecordUuid())) {
                restoreRunning(activity);
            }
        }
    }

    private java.util.List<AgentActivity> openActivities(AgentDevice device) {
        return activityRepository.findByDeviceUuidAndStateForUpdate(
                device.getDeviceUuid(), ActivityState.OPEN);
    }

    private void temporarilyCloseLegacyRunning(
            Long legacyDeviceId,
            LocalDateTime requestedEnd,
            boolean explicitShutdown) {
        if (legacyDeviceId == null) {
            return;
        }
        String activityStatus = explicitShutdown ? "CLOSED" : "OFFLINE";
        String sessionStatus = explicitShutdown ? "SHUTDOWN" : "OFFLINE";

        for (ProcessActivity target : processRepository.findByDeviceIdAndStatus(
                legacyDeviceId, "RUNNING")) {
            LocalDateTime end = safeEnd(target.getStartTime(), requestedEnd);
            target.setEndTime(end);
            target.setDurationSeconds(durationSeconds(target.getStartTime(), end));
            target.setStatus(activityStatus);
            processRepository.save(target);
        }
        for (ActiveWindowActivity target : windowRepository.findByDeviceIdAndStatus(
                legacyDeviceId, "RUNNING")) {
            LocalDateTime end = safeEnd(target.getStartTime(), requestedEnd);
            target.setEndTime(end);
            target.setDurationSeconds(durationSeconds(target.getStartTime(), end));
            target.setStatus(activityStatus);
            windowRepository.save(target);
        }
        for (IdleActivity target : idleRepository.findByDeviceIdAndStatus(
                legacyDeviceId, "RUNNING")) {
            LocalDateTime end = safeEnd(target.getIdleStart(), requestedEnd);
            target.setIdleEnd(end);
            target.setIdleSeconds(durationSeconds(target.getIdleStart(), end));
            target.setStatus(activityStatus);
            idleRepository.save(target);
        }
        for (DeviceSession target : sessionRepository.findByDeviceIdAndStatus(
                legacyDeviceId, "RUNNING")) {
            LocalDateTime end = safeEnd(target.getStartupTime(), requestedEnd);
            target.setShutdownTime(end);
            target.setSessionDurationSeconds(durationSeconds(target.getStartupTime(), end));
            target.setStatus(sessionStatus);
            sessionRepository.save(target);
        }
    }

    /**
     * A heartbeat/lifecycle timestamp can occasionally be older than a newly uploaded activity
     * start because requests are concurrent or the client clock moved. In that case the row is
     * marked OFFLINE/SHUTDOWN but no end time is fabricated. A later authoritative agent revision
     * will provide the real end time.
     */
    private static LocalDateTime safeEnd(LocalDateTime start, LocalDateTime requestedEnd) {
        if (requestedEnd == null) {
            return null;
        }
        if (start != null && requestedEnd.isBefore(start)) {
            return null;
        }
        return requestedEnd;
    }

    private static Long durationSeconds(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return Math.max(0L, Duration.between(start, end).toSeconds());
    }

    private void temporarilyClose(
            AgentActivity source,
            LocalDateTime requestedEnd,
            boolean explicitShutdown) {
        if (source.getLegacyRecordId() == null) {
            return;
        }
        LocalDateTime end = safeEnd(source.getStartedAt(), requestedEnd);
        Long durationSeconds = durationSeconds(source.getStartedAt(), end);
        String status = explicitShutdown
                ? (source.getKind() == ActivityKind.DEVICE_SESSION ? "SHUTDOWN" : "CLOSED")
                : "OFFLINE";

        switch (source.getKind()) {
            case PROCESS -> processRepository.findById(source.getLegacyRecordId()).ifPresent(target -> {
                target.setEndTime(end);
                target.setDurationSeconds(durationSeconds);
                target.setStatus(status);
                processRepository.save(target);
            });
            case ACTIVE_WINDOW -> windowRepository.findById(source.getLegacyRecordId()).ifPresent(target -> {
                target.setEndTime(end);
                target.setDurationSeconds(durationSeconds);
                target.setStatus(status);
                windowRepository.save(target);
            });
            case IDLE -> idleRepository.findById(source.getLegacyRecordId()).ifPresent(target -> {
                target.setIdleEnd(end);
                target.setIdleSeconds(durationSeconds);
                target.setStatus(status);
                idleRepository.save(target);
            });
            case DEVICE_SESSION -> sessionRepository.findById(source.getLegacyRecordId()).ifPresent(target -> {
                target.setShutdownTime(end);
                target.setSessionDurationSeconds(durationSeconds);
                target.setStatus(status);
                sessionRepository.save(target);
            });
        }
    }

    private void restoreRunning(AgentActivity source) {
        if (source.getLegacyRecordId() == null) {
            return;
        }
        switch (source.getKind()) {
            case PROCESS -> processRepository.findById(source.getLegacyRecordId()).ifPresent(target -> {
                target.setEndTime(null);
                target.setDurationSeconds(null);
                target.setStatus("RUNNING");
                processRepository.save(target);
            });
            case ACTIVE_WINDOW -> windowRepository.findById(source.getLegacyRecordId()).ifPresent(target -> {
                target.setEndTime(null);
                target.setDurationSeconds(null);
                target.setStatus("RUNNING");
                windowRepository.save(target);
            });
            case IDLE -> idleRepository.findById(source.getLegacyRecordId()).ifPresent(target -> {
                target.setIdleEnd(null);
                target.setIdleSeconds(null);
                target.setStatus("RUNNING");
                idleRepository.save(target);
            });
            case DEVICE_SESSION -> sessionRepository.findById(source.getLegacyRecordId()).ifPresent(target -> {
                target.setShutdownTime(null);
                target.setSessionDurationSeconds(null);
                target.setStatus("RUNNING");
                sessionRepository.save(target);
            });
        }
    }
}
