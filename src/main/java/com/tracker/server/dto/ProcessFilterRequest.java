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
public class ProcessFilterRequest {
    private Long userId;
    private Long deviceId;
    private String processName;
    private String status;
    private Long pid;
    
    // Individual date filters
    private LocalDateTime startTimeFrom;
    private LocalDateTime startTimeTo;
    private LocalDateTime endTimeFrom;
    private LocalDateTime endTimeTo;
    
    // Global date range filters (applies to both startTime and endTime)
    private LocalDateTime fromDate;
    private LocalDateTime endDate;
    
    private Long durationMin;
    private Long durationMax;
}