package com.tracker.server.agent.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record AgentSyncRequest(
        @NotEmpty @Size(max = 100) List<@Valid ActivitySnapshotRequest> records) {
}
