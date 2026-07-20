package com.tracker.server.dto.dashboard;

import java.util.List;

public record ActivityTableResponse<T>(
        String type,
        List<T> rows,
        long totalElements,
        int page,
        int size,
        int totalPages,
        Long filteredDurationSeconds) {
}
