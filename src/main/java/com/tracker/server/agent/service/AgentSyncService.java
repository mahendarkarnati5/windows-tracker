package com.tracker.server.agent.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.tracker.server.agent.dto.ActivityAcknowledgement;
import com.tracker.server.agent.dto.ActivitySnapshotRequest;
import com.tracker.server.agent.dto.AgentSyncRequest;
import com.tracker.server.agent.dto.AgentSyncResponse;
import com.tracker.server.agent.entity.AgentDevice;
import com.tracker.server.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentSyncService {

    private final AgentDeviceService deviceService;
    private final AgentRecordUpsertService upsertService;

    public AgentSyncResponse synchronize(
            String username,
            String deviceUuid,
            AgentSyncRequest request,
            String remoteAddress) {

        AgentDevice device = deviceService.activitySeen(
                username,
                deviceUuid,
                request.sessionUuid(),
                request.sessionStartedAt(),
                request.sessionSequence(),
                request.sentAt(),
                remoteAddress);
        User user = deviceService.requireUser(username);
        List<ActivityAcknowledgement> acknowledgements = new ArrayList<>(request.records().size());

        for (ActivitySnapshotRequest record : request.records()) {
            try {
                acknowledgements.add(upsertService.apply(device, user, record));
            } catch (ResponseStatusException ex) {
                acknowledgements.add(new ActivityAcknowledgement(
                        record.recordUuid(),
                        0,
                        "REJECTED",
                        ex.getReason() == null ? "Record validation failed" : ex.getReason()));
            }
        }

        return new AgentSyncResponse(List.copyOf(acknowledgements), Instant.now());
    }
}
