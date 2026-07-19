package com.tracker.server.agent.dto;

public record AgentEnrollmentResponse(
        String deviceUuid,
        Long deviceId,
        String deviceToken) {
}
