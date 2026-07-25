package com.tracker.server.agent.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.tracker.server.agent.repository.AgentDeviceRepository;
import com.tracker.server.repository.DeviceRepository;

import lombok.extern.slf4j.Slf4j;

/** Marks stale devices OFFLINE without touching activity rows. */
@Component
@Slf4j
public class AgentPresenceScheduler {

    private final AgentDeviceRepository agentDeviceRepository;
    private final DeviceRepository deviceRepository;
    private final TransactionTemplate transactionTemplate;

    @Value("${tracker.agent.offline-after-seconds:45}")
    private long offlineAfterSeconds;

    @Value("${tracker.agent.presence-batch-size:100}")
    private int batchSize;

    public AgentPresenceScheduler(
            AgentDeviceRepository agentDeviceRepository,
            DeviceRepository deviceRepository,
            PlatformTransactionManager transactionManager) {
        this.agentDeviceRepository = agentDeviceRepository;
        this.deviceRepository = deviceRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(5);
    }

    @Scheduled(fixedDelayString = "${tracker.agent.presence-check-ms:15000}")
    public void markStaleDevicesOffline() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime cutoff = now.minusSeconds(Math.max(20L, offlineAfterSeconds));
        int limit = Math.max(1, Math.min(batchSize, 500));

        var stale = agentDeviceRepository
                .findByLastSeenAtBeforeAndLifecycleStateOrderByLastSeenAtAsc(
                        cutoff,
                        AgentDeviceService.LIFECYCLE_ONLINE,
                        PageRequest.of(0, limit));
        for (var device : stale) {
            try {
                markOneOffline(device.getId(), cutoff, now);
            } catch (RuntimeException ex) {
                log.warn("Presence update skipped one device: {}", ex.getMessage());
            }
        }
    }

    private void markOneOffline(Long mappingId, LocalDateTime cutoff, LocalDateTime now) {
        transactionTemplate.executeWithoutResult(status -> {
            var mapping = agentDeviceRepository.findByIdForUpdate(mappingId).orElse(null);
            if (mapping == null
                    || mapping.getLastSeenAt() == null
                    || !mapping.getLastSeenAt().isBefore(cutoff)
                    || !AgentDeviceService.LIFECYCLE_ONLINE.equalsIgnoreCase(
                            mapping.getLifecycleState())) {
                return;
            }

            mapping.setLifecycleState(AgentDeviceService.LIFECYCLE_OFFLINE);
            mapping.setUpdatedAt(now);
            agentDeviceRepository.save(mapping);

            deviceRepository.findById(mapping.getLegacyDeviceId()).ifPresent(device -> {
                device.setOnline(false);
                device.setStatus(AgentDeviceService.LIFECYCLE_OFFLINE);
                // Keep lastSeen unchanged. Dashboard uses it as the frozen duration boundary.
                deviceRepository.save(device);
            });
        });
    }
}
