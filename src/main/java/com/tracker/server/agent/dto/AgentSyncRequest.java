package com.tracker.server.agent.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AgentSyncRequest(
        @NotBlank String sessionUuid,
        @NotNull Instant sessionStartedAt,
        @Positive long sessionSequence,
        @NotNull Instant sentAt,
        boolean backlogCompleteAfterBatch,
        @NotNull @Size(max = 250) List<@Valid ActivitySnapshotRequest> records) {
}
