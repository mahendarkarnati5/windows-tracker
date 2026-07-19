package com.tracker.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceSessionFilterRequest {
    private Long userId;
    private Long deviceId;
    private String status;
    
    // Individual date filters
    private LocalDateTime startupTimeFrom;
    private LocalDateTime startupTimeTo;
    private LocalDateTime shutdownTimeFrom;
    private LocalDateTime shutdownTimeTo;
    
    // Global date range filters (applies to both startupTime and shutdownTime)
    private LocalDateTime fromDate;
    private LocalDateTime endDate;
    
    private Long sessionDurationMin;
    private Long sessionDurationMax;
}