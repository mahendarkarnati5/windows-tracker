package com.tracker.server.dto.dashboard;

import java.time.LocalDateTime;

public record ProcessActivityRow(
        Long id,
        Long pid,
        String processName,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationSeconds) {
}
