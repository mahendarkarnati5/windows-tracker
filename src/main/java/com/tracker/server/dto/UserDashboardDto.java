package com.tracker.server.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserDashboardDto {

    private Long totalActiveTime;
    private Long totalIdleTime;
    private Long totalSessions;
    private String topAppUsed;
    private String lastLoginTime;
}