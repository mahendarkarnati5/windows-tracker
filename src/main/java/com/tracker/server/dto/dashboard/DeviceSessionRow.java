package com.tracker.server.dto.dashboard;

import java.time.LocalDateTime;

public record DeviceSessionRow(
        Long id,
        String status,
        LocalDateTime startupTime,
        LocalDateTime shutdownTime,
        Long durationSeconds) {
}
