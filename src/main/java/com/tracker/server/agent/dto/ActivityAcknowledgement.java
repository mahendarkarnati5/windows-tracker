package com.tracker.server.agent.dto;

public record ActivityAcknowledgement(
        String recordUuid,
        long acceptedRevision,
        String status,
        String error) {
}
