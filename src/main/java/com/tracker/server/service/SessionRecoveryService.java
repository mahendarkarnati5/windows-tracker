package com.tracker.server.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.tracker.server.entity.DeviceSession;
import com.tracker.server.repository.DeviceSessionRepository;
import com.tracker.server.util.DateTimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@ConditionalOnProperty(name = "tracker.legacy-recovery.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SessionRecoveryService {

    private final DeviceSessionRepository repository;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverRunningSessions() {

        List<DeviceSession> sessions =
                repository.findByStatus("RUNNING");

        for (DeviceSession session : sessions) {

            LocalDateTime end = DateTimeUtil.now();

            session.setShutdownTime(end);

            session.setSessionDurationSeconds(
                    Duration.between(
                            session.getStartupTime(),
                            end)
                            .getSeconds());

            session.setStatus("RECOVERED");

            repository.save(session);

            log.info(
                    "Recovered Session {}",
                    session.getId());
        }
    }
}
