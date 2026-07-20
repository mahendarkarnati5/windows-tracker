package com.tracker.server.dto.dashboard;

import java.time.LocalDateTime;

public record AdminUserListItemResponse(
        Long id,
        String username,
        LocalDateTime createdAt,
        long deviceCount,
        long onlineDeviceCount) {
}
