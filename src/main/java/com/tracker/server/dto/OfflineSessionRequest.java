package com.tracker.server.dto;


import java.time.LocalDateTime;

import lombok.Data;

@Data
public class OfflineSessionRequest {

    private Long deviceId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}