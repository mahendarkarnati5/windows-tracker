package com.tracker.server.agent.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tracker.server.agent.entity.AgentDevice;
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
 * Device OFFLINE is a display state only. It must not fabricate end times or
 * rewrite every running activity row. An explicit shutdown is different: its
 * timestamp closes every still-running projection.
 */
@Service
@RequiredArgsConstructor
public class AgentProjectionService {

    private final ProcessActivityRepository processRepository;
    private final ActiveWindowActivityRepository windowRepository;
    private final IdleActivityRepository idleRepository;
    private final DeviceSessionRepository sessionRepository;

    public void temporarilyCloseForOffline(AgentDevice device, LocalDateTime lastSeenAt) {
        // Intentionally empty. The dashboard displays RUNNING rows as OFFLINE and freezes
        // their duration at device.lastSeen while keeping end time NULL.
    }

    @Transactional
    public void temporarilyCloseForShutdown(AgentDevice device, LocalDateTime shutdownAt) {
        closeAllRunning(device.getLegacyDeviceId(), shutdownAt, true);
    }

    public void temporarilyCloseLegacyOnly(
            Long legacyDeviceId, LocalDateTime requestedEnd, boolean explicitShutdown) {
        if (explicitShutdown) {
            closeAllRunning(legacyDeviceId, requestedEnd, true);
        }
    }

    public void reconcileOpenRecords(AgentDevice device, java.util.Set<String> agentOpenRecordUuids) {
        // No restoration query is required because OFFLINE never changes activity rows.
    }

    @Transactional
    public void closeAllRunning(Long deviceId, LocalDateTime endAt, boolean shutdown) {
        if (deviceId == null || endAt == null) {
            return;
        }
        for (ProcessActivity row : processRepository.findByDeviceIdAndStatus(deviceId, "RUNNING")) {
            LocalDateTime end = safeEnd(row.getStartTime(), endAt);
            row.setEndTime(end);
            row.setDurationSeconds(durationSeconds(row.getStartTime(), end));
            row.setStatus("CLOSED");
        }
        for (ActiveWindowActivity row : windowRepository.findByDeviceIdAndStatus(deviceId, "RUNNING")) {
            LocalDateTime end = safeEnd(row.getStartTime(), endAt);
            row.setEndTime(end);
            row.setDurationSeconds(durationSeconds(row.getStartTime(), end));
            row.setStatus("CLOSED");
        }
        for (IdleActivity row : idleRepository.findByDeviceIdAndStatus(deviceId, "RUNNING")) {
            LocalDateTime end = safeEnd(row.getIdleStart(), endAt);
            row.setIdleEnd(end);
            row.setIdleSeconds(durationSeconds(row.getIdleStart(), end));
            row.setStatus("CLOSED");
        }
        for (DeviceSession row : sessionRepository.findByDeviceIdAndStatus(deviceId, "RUNNING")) {
            LocalDateTime end = safeEnd(row.getStartupTime(), endAt);
            row.setShutdownTime(end);
            row.setSessionDurationSeconds(durationSeconds(row.getStartupTime(), end));
            row.setStatus(shutdown ? "SHUTDOWN" : "CLOSED");
        }
    }

    private static LocalDateTime safeEnd(LocalDateTime start, LocalDateTime requestedEnd) {
        if (start == null) {
            return requestedEnd;
        }
        return requestedEnd.isBefore(start) ? start : requestedEnd;
    }

    private static Long durationSeconds(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return Math.max(0L, Duration.between(start, end).toSeconds());
    }
}
