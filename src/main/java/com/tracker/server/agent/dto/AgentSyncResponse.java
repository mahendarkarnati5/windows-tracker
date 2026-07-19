package com.tracker.server.agent.dto;

import java.time.Instant;
import java.util.List;

public record AgentSyncResponse(
        List<ActivityAcknowledgement> acknowledgements,
        Instant serverTime) {
}
