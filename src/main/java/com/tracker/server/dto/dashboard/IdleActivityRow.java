package com.tracker.server.dto.dashboard;

import java.time.LocalDateTime;

public record IdleActivityRow(
        Long id,
        String status,
        LocalDateTime idleStart,
        LocalDateTime idleEnd,
        Long durationSeconds) {
}
