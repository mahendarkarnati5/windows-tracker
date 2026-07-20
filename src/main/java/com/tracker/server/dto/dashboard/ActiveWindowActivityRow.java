package com.tracker.server.dto.dashboard;

import java.time.LocalDateTime;

public record ActiveWindowActivityRow(
        Long id,
        String windowTitle,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationSeconds) {
}
