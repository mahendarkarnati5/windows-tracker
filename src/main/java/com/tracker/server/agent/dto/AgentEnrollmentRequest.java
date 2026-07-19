package com.tracker.server.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentEnrollmentRequest(
        @NotBlank @Size(max = 36) String deviceUuid,
        @NotBlank @Size(max = 255) String machineName,
        @NotBlank @Size(max = 255) String osName,
        @Size(max = 64) String agentVersion,
        @Size(max = 255) String macAddress) {
}
