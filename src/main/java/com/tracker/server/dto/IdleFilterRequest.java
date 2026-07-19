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
public class IdleFilterRequest {
    private Long userId;
    private Long deviceId;
    private String status;
    
    // Individual date filters
    private LocalDateTime idleStartFrom;
    private LocalDateTime idleStartTo;
    private LocalDateTime idleEndFrom;
    private LocalDateTime idleEndTo;
    
    // Global date range filters (applies to both idleStart and idleEnd)
    private LocalDateTime fromDate;
    private LocalDateTime endDate;
    
    private Long idleSecondsMin;
    private Long idleSecondsMax;
}