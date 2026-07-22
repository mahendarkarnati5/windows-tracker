package com.tracker.server.agent.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tracker.server.agent.repository.AgentDeviceRepository;
import com.tracker.server.repository.ActiveWindowActivityRepository;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.repository.DeviceSessionRepository;
import com.tracker.server.repository.IdleActivityRepository;
import com.tracker.server.repository.ProcessActivityRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AgentPresenceScheduler {

    private final AgentDeviceRepository agentDeviceRepository;
    private final DeviceRepository deviceRepository;
    private final ProcessActivityRepository processRepository;
    private final ActiveWindowActivityRepository windowRepository;
    private final IdleActivityRepository idleRepository;
    private final DeviceSessionRepository sessionRepository;
    private final AgentProjectionService projectionService;

    @Value("${tracker.agent.offline-after-seconds:45}")
    private long offlineAfterSeconds;

    @Value("${tracker.agent.orphan-repair-ms:60000}")
    private long orphanRepairIntervalMs;

    private volatile long nextOrphanRepairAtMillis;

    @Scheduled(fixedDelayString = "${tracker.agent.presence-check-ms:10000}")
    @Transactional
    public void markStaleDevicesOffline() {
        LocalDateTime serverNow = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime cutoff = serverNow.minusSeconds(Math.max(20L, offlineAfterSeconds));

        for (var stale : agentDeviceRepository.findByLastSeenAtBeforeAndLifecycleState(
                cutoff, AgentDeviceService.LIFECYCLE_ONLINE)) {
            var mapping = agentDeviceRepository.findByIdForUpdate(stale.getId()).orElse(null);
            if (mapping == null
                    || mapping.getLastSeenAt() == null
                    || !mapping.getLastSeenAt().isBefore(cutoff)
                    || !AgentDeviceService.LIFECYCLE_ONLINE.equalsIgnoreCase(
                            mapping.getLifecycleState())) {
                continue;
            }

            LocalDateTime temporaryEnd = mapping.getLastLifecycleAt() == null
                    ? mapping.getLastSeenAt()
                    : mapping.getLastLifecycleAt();
            projectionService.temporarilyCloseForOffline(mapping, temporaryEnd);
            mapping.setLifecycleState(AgentDeviceService.LIFECYCLE_OFFLINE);
            mapping.setUpdatedAt(serverNow);
            agentDeviceRepository.save(mapping);

            deviceRepository.findById(mapping.getLegacyDeviceId()).ifPresent(device -> {
                device.setOnline(false);
                device.setStatus(AgentDeviceService.LIFECYCLE_OFFLINE);
                device.setLastSeen(temporaryEnd);
                deviceRepository.save(device);
            });
        }

        // Compatibility path for devices created by the older API.
        for (var legacy : deviceRepository.findByOnlineTrueAndLastSeenBefore(cutoff)) {
            LocalDateTime temporaryEnd = legacy.getLastSeen();
            projectionService.temporarilyCloseLegacyOnly(
                    legacy.getId(), temporaryEnd, false);
            legacy.setOnline(false);
            legacy.setStatus(AgentDeviceService.LIFECYCLE_OFFLINE);
            deviceRepository.save(legacy);
        }

        long nowMillis = System.currentTimeMillis();
        if (nowMillis >= nextOrphanRepairAtMillis) {
            nextOrphanRepairAtMillis = nowMillis + Math.max(10_000L, orphanRepairIntervalMs);
            repairOrphanRunningRows(cutoff, serverNow);
        }
    }

    /**
     * Repairs rows left as RUNNING by older deployments, server restarts, or a previously missed
     * timeout. This does not touch a healthy online device. When the stored last-seen time is older
     * than a row's start time, the projection becomes OFFLINE/SHUTDOWN with a null end time instead
     * of producing a negative duration.
     */
    private void repairOrphanRunningRows(LocalDateTime cutoff, LocalDateTime serverNow) {
        Set<Long> deviceIds = new HashSet<>();
        deviceIds.addAll(processRepository.findDeviceIdsWithRunningRows());
        deviceIds.addAll(windowRepository.findDeviceIdsWithRunningRows());
        deviceIds.addAll(idleRepository.findDeviceIdsWithRunningRows());
        deviceIds.addAll(sessionRepository.findDeviceIdsWithRunningRows());

        for (Long deviceId : deviceIds) {
            deviceRepository.findById(deviceId).ifPresent(device -> {
                boolean healthyOnline = device.isOnline()
                        && device.getLastSeen() != null
                        && !device.getLastSeen().isBefore(cutoff);
                if (healthyOnline) {
                    return;
                }

                LocalDateTime temporaryEnd = device.getLastSeen() == null
                        ? serverNow
                        : device.getLastSeen();
                boolean shutdown = AgentDeviceService.LIFECYCLE_SHUTDOWN.equalsIgnoreCase(
                        device.getStatus());
                projectionService.temporarilyCloseLegacyOnly(
                        deviceId, temporaryEnd, shutdown);

                if (device.isOnline()) {
                    device.setOnline(false);
                    device.setStatus(AgentDeviceService.LIFECYCLE_OFFLINE);
                    deviceRepository.save(device);
                }
            });
        }
    }
}
