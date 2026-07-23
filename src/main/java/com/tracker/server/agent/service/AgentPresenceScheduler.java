package com.tracker.server.agent.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.tracker.server.agent.repository.AgentDeviceRepository;
import com.tracker.server.repository.ActiveWindowActivityRepository;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.repository.DeviceSessionRepository;
import com.tracker.server.repository.IdleActivityRepository;
import com.tracker.server.repository.ProcessActivityRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Presence reconciliation intentionally uses short, per-device transactions.
 * A slow/offline device must never keep one database transaction open while all
 * other devices are being scanned.
 */
@Component
@Slf4j
public class AgentPresenceScheduler {

    private final AgentDeviceRepository agentDeviceRepository;
    private final DeviceRepository deviceRepository;
    private final ProcessActivityRepository processRepository;
    private final ActiveWindowActivityRepository windowRepository;
    private final IdleActivityRepository idleRepository;
    private final DeviceSessionRepository sessionRepository;
    private final AgentProjectionService projectionService;
    private final TransactionTemplate transactionTemplate;

    @Value("${tracker.agent.offline-after-seconds:45}")
    private long offlineAfterSeconds;

    @Value("${tracker.agent.orphan-repair-ms:300000}")
    private long orphanRepairIntervalMs;

    @Value("${tracker.agent.presence-batch-size:50}")
    private int batchSize;

    private volatile long nextOrphanRepairAtMillis;

    public AgentPresenceScheduler(
            AgentDeviceRepository agentDeviceRepository,
            DeviceRepository deviceRepository,
            ProcessActivityRepository processRepository,
            ActiveWindowActivityRepository windowRepository,
            IdleActivityRepository idleRepository,
            DeviceSessionRepository sessionRepository,
            AgentProjectionService projectionService,
            PlatformTransactionManager transactionManager) {
        this.agentDeviceRepository = agentDeviceRepository;
        this.deviceRepository = deviceRepository;
        this.processRepository = processRepository;
        this.windowRepository = windowRepository;
        this.idleRepository = idleRepository;
        this.sessionRepository = sessionRepository;
        this.projectionService = projectionService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(10);
    }

    @Scheduled(fixedDelayString = "${tracker.agent.presence-check-ms:15000}")
    public void markStaleDevicesOffline() {
        LocalDateTime serverNow = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime cutoff = serverNow.minusSeconds(Math.max(20L, offlineAfterSeconds));
        int limit = Math.max(1, Math.min(batchSize, 250));

        var staleMappings = agentDeviceRepository
                .findByLastSeenAtBeforeAndLifecycleStateOrderByLastSeenAtAsc(
                        cutoff,
                        AgentDeviceService.LIFECYCLE_ONLINE,
                        PageRequest.of(0, limit));
        for (var stale : staleMappings) {
            runSafely(() -> markAgentDeviceOffline(stale.getId(), cutoff, serverNow));
        }

        // Compatibility path for devices created by the older API. Limit it too,
        // otherwise one old installation can monopolize the connection pool.
        var staleLegacy = deviceRepository.findByOnlineTrueAndLastSeenBeforeOrderByLastSeenAsc(
                cutoff, PageRequest.of(0, limit));
        for (var legacy : staleLegacy) {
            runSafely(() -> markLegacyDeviceOffline(legacy.getId(), cutoff));
        }

        long nowMillis = System.currentTimeMillis();
        if (nowMillis >= nextOrphanRepairAtMillis) {
            nextOrphanRepairAtMillis = nowMillis + Math.max(60_000L, orphanRepairIntervalMs);
            repairOrphanRunningRows(cutoff, serverNow, limit);
        }
    }

    private void markAgentDeviceOffline(Long mappingId, LocalDateTime cutoff, LocalDateTime serverNow) {
        transactionTemplate.executeWithoutResult(status -> {
            var mapping = agentDeviceRepository.findByIdForUpdate(mappingId).orElse(null);
            if (mapping == null
                    || mapping.getLastSeenAt() == null
                    || !mapping.getLastSeenAt().isBefore(cutoff)
                    || !AgentDeviceService.LIFECYCLE_ONLINE.equalsIgnoreCase(
                            mapping.getLifecycleState())) {
                return;
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
        });
    }

    private void markLegacyDeviceOffline(Long deviceId, LocalDateTime cutoff) {
        transactionTemplate.executeWithoutResult(status ->
                deviceRepository.findByIdForUpdate(deviceId).ifPresent(device -> {
                    if (!device.isOnline()
                            || device.getLastSeen() == null
                            || !device.getLastSeen().isBefore(cutoff)) {
                        return;
                    }
                    LocalDateTime temporaryEnd = device.getLastSeen();
                    projectionService.temporarilyCloseLegacyOnly(
                            device.getId(), temporaryEnd, false);
                    device.setOnline(false);
                    device.setStatus(AgentDeviceService.LIFECYCLE_OFFLINE);
                    deviceRepository.save(device);
                }));
    }

    private void repairOrphanRunningRows(
            LocalDateTime cutoff, LocalDateTime serverNow, int limit) {
        Set<Long> deviceIds = new LinkedHashSet<>();
        addUpToLimit(deviceIds, processRepository.findDeviceIdsWithRunningRows(), limit);
        addUpToLimit(deviceIds, windowRepository.findDeviceIdsWithRunningRows(), limit);
        addUpToLimit(deviceIds, idleRepository.findDeviceIdsWithRunningRows(), limit);
        addUpToLimit(deviceIds, sessionRepository.findDeviceIdsWithRunningRows(), limit);

        for (Long deviceId : deviceIds) {
            runSafely(() -> repairOneOrphanDevice(deviceId, cutoff, serverNow));
        }
    }

    private void repairOneOrphanDevice(
            Long deviceId, LocalDateTime cutoff, LocalDateTime serverNow) {
        transactionTemplate.executeWithoutResult(status ->
                deviceRepository.findByIdForUpdate(deviceId).ifPresent(device -> {
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
                }));
    }

    private static void addUpToLimit(Set<Long> target, Iterable<Long> values, int limit) {
        for (Long value : values) {
            if (target.size() >= limit) {
                return;
            }
            target.add(value);
        }
    }

    private void runSafely(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException ex) {
            // One damaged/stuck device must not prevent the remaining devices from being checked.
            log.warn("Presence reconciliation skipped one device: {}", ex.getMessage());
        }
    }
}
