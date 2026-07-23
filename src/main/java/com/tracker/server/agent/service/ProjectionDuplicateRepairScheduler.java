package com.tracker.server.agent.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "tracker.agent.duplicate-repair-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ProjectionDuplicateRepairScheduler {

    private final ProjectionDuplicateRepairService repairService;

    @EventListener(ApplicationReadyEvent.class)
    public void repairAfterStartup() {
        runSafely("startup");
    }

    @Scheduled(
            fixedDelayString = "${tracker.agent.duplicate-repair-ms:300000}",
            initialDelayString = "${tracker.agent.duplicate-repair-initial-ms:60000}")
    public void repairPeriodically() {
        runSafely("scheduled");
    }

    private void runSafely(String trigger) {
        try {
            if ("startup".equals(trigger)) {
                repairService.repairAll();
            } else {
                repairService.repairIncremental();
            }
        } catch (RuntimeException ex) {
            // Duplicate repair must never prevent the server from starting or accepting sync.
            log.error("Activity projection duplicate repair failed ({})", trigger, ex);
        }
    }
}
