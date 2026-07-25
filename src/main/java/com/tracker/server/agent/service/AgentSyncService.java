package com.tracker.server.agent.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tracker.server.agent.dto.ActivityAcknowledgement;
import com.tracker.server.agent.dto.ActivitySnapshotRequest;
import com.tracker.server.agent.dto.AgentSyncRequest;
import com.tracker.server.agent.dto.AgentSyncResponse;
import com.tracker.server.agent.entity.AgentActivity;
import com.tracker.server.agent.entity.AgentDevice;
import com.tracker.server.agent.repository.AgentActivityRepository;
import com.tracker.server.entity.Device;
import com.tracker.server.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentSyncService {

    private final AgentDeviceService deviceService;
    private final AgentRecordUpsertService upsertService;
    private final AgentActivityRepository activityRepository;

    @Transactional
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
                request.backlogCompleteAfterBatch(),
                remoteAddress);
        User user = deviceService.requireUser(username);
        Device legacyDevice = upsertService.requireLegacyDevice(device);
        Map<String, AgentActivity> currentByUuid = activityRepository.findAllById(
                        request.records().stream()
                                .map(ActivitySnapshotRequest::recordUuid)
                                .filter(value -> {
                                    try {
                                        UUID.fromString(value);
                                        return true;
                                    } catch (RuntimeException ignored) {
                                        return false;
                                    }
                                })
                                .map(value -> UUID.fromString(value).toString())
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(
                        AgentActivity::getRecordUuid,
                        Function.identity()));

        List<ActivityAcknowledgement> acknowledgements = new ArrayList<>(request.records().size());
        Set<String> seenRecordUuids = new HashSet<>();

        for (ActivitySnapshotRequest record : request.records()) {
            try {
                String canonicalUuid = UUID.fromString(record.recordUuid()).toString();
                if (!seenRecordUuids.add(canonicalUuid)) {
                    acknowledgements.add(new ActivityAcknowledgement(
                            canonicalUuid, 0, "REJECTED", "Duplicate record UUID in batch"));
                    continue;
                }
                acknowledgements.add(upsertService.applyInBatch(
                        device, user, legacyDevice, record, currentByUuid.get(canonicalUuid)));
            } catch (ResponseStatusException | IllegalArgumentException ex) {
                String error = ex instanceof ResponseStatusException response
                        ? (response.getReason() == null ? "Record validation failed" : response.getReason())
                        : "Invalid record UUID";
                acknowledgements.add(new ActivityAcknowledgement(
                        record.recordUuid(),
                        0,
                        "REJECTED",
                        error));
            }
        }

        return new AgentSyncResponse(List.copyOf(acknowledgements), Instant.now());
    }
}
