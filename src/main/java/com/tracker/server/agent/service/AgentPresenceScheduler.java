package com.tracker.server.agent.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tracker.server.agent.repository.AgentDeviceRepository;
import com.tracker.server.repository.DeviceRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AgentPresenceScheduler {

    private final AgentDeviceRepository agentDeviceRepository;
    private final DeviceRepository deviceRepository;

    @Value("${tracker.agent.offline-after-seconds:150}")
    private long offlineAfterSeconds;

    @Scheduled(fixedDelayString = "${tracker.agent.presence-check-ms:60000}")
    @Transactional
    public void markStaleDevicesOffline() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC)
                .minusSeconds(Math.max(60L, offlineAfterSeconds));
        agentDeviceRepository.findByLastSeenAtBefore(cutoff).forEach(mapping ->
                deviceRepository.findById(mapping.getLegacyDeviceId()).ifPresent(device -> {
                    if (device.isOnline()) {
                        device.setOnline(false);
                        deviceRepository.save(device);
                    }
                }));
    }
}
