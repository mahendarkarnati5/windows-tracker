package com.tracker.server.agent.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentPresenceRequest(
        @NotNull Instant observedAt,
        @NotBlank @Size(max = 36) String sessionUuid,
        @NotNull Instant sessionStartedAt,
        @Min(1) long sessionSequence,
        @Size(max = 10000) List<String> openRecordUuids) {

    public AgentPresenceRequest {
        openRecordUuids = openRecordUuids == null ? List.of() : List.copyOf(openRecordUuids);
    }
}
