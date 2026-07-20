package com.tracker.server.dto.dashboard;

import java.time.LocalDateTime;

public record AdminDeviceListItemResponse(
        Long id,
        Long userId,
        String username,
        String machineName,
        String osName,
        String lastIpAddress,
        LocalDateTime lastSeen,
        String displayStatus,
        boolean online,
        LocalDateTime latestSessionStart,
        LocalDateTime latestSessionEnd,
        Long latestSessionDurationSeconds) {
}
