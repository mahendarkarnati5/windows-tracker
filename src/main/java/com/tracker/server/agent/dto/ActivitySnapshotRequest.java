package com.tracker.server.agent.dto;

import java.time.Instant;

import com.tracker.server.agent.model.ActivityKind;
import com.tracker.server.agent.model.ActivityState;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ActivitySnapshotRequest(
        @NotBlank @Size(max = 36) String recordUuid,
        @NotNull ActivityKind kind,
        @Min(1) long revision,
        @NotNull Instant startedAt,
        Instant endedAt,
        @NotNull ActivityState state,
        @Size(max = 64) String closeReason,
        Long processId,
        @Size(max = 2048) String processName,
        @Size(max = 4096) String windowTitle,
        Long legacyRecordId) {
}
