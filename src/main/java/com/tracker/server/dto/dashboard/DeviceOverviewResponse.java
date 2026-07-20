package com.tracker.server.dto.dashboard;

import java.time.LocalDateTime;

public record DeviceOverviewResponse(
        Long id,
        Long userId,
        String username,
        String machineName,
        String osName,
        String lastIpAddress,
        LocalDateTime lastSeen,
        String displayStatus,
        long todayTotalProcesses,
        long runningProcesses,
        SessionSummary currentSession,
        IdleSummary currentIdle,
        WindowSummary currentActiveWindow) {

    public record SessionSummary(
            Long id,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long durationSeconds) {
    }

    public record IdleSummary(
            Long id,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long durationSeconds) {
    }

    public record WindowSummary(
            Long id,
            String title,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long durationSeconds) {
    }
}
