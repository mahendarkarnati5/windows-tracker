package com.tracker.server.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tracker.server.dto.dashboard.ActivityTableResponse;
import com.tracker.server.dto.dashboard.AdminDashboardSummaryResponse;
import com.tracker.server.dto.dashboard.AdminDeviceListItemResponse;
import com.tracker.server.dto.dashboard.AdminUserListItemResponse;
import com.tracker.server.dto.dashboard.DeviceOverviewResponse;
import com.tracker.server.service.AdminDashboardService;


@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public AdminDashboardSummaryResponse summary() {
        return dashboardService.getSummary();
    }

    @GetMapping("/users")
    public List<AdminUserListItemResponse> users(
            @RequestParam(required = false) String search) {
        return dashboardService.getUsers(search);
    }

    @GetMapping("/devices")
    public List<AdminDeviceListItemResponse> devices(
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String search) {
        return dashboardService.getDevices(scope, userId, search);
    }

    @GetMapping("/devices/{deviceId}/overview")
    public DeviceOverviewResponse deviceOverview(
            @PathVariable Long deviceId,
            @RequestParam(defaultValue = "UTC") String timezone) {
        return dashboardService.getDeviceOverview(deviceId, timezone);
    }

    @GetMapping("/devices/{deviceId}/activities/{type}")
    public ActivityTableResponse<?> activities(
            @PathVariable Long deviceId,
            @PathVariable String type,
            @RequestParam(defaultValue = "TODAY") String datePreset,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "false") boolean includeTotalDuration) {
        return dashboardService.getActivities(
                deviceId,
                type,
                datePreset,
                timezone,
                from,
                to,
                status,
                search,
                page,
                size,
                sortBy,
                sortDir,
                includeTotalDuration);
    }
}
