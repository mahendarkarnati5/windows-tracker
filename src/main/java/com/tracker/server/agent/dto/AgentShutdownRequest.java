package com.tracker.server.agent.dto;

import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentShutdownRequest(
        @NotNull Instant shutdownAt,
        @NotBlank @Size(max = 36) String sessionUuid,
        @NotNull Instant sessionStartedAt,
        @Min(1) long sessionSequence,
        @NotBlank @Size(max = 64) String reason) {
}
