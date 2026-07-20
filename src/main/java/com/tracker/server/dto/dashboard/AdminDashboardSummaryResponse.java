package com.tracker.server.dto.dashboard;

public record AdminDashboardSummaryResponse(
        long totalDevices,
        long onlineDevices,
        long offlineDevices,
        long shutdownDevices,
        long totalUsers) {
}
