package com.tracker.server.scheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.service.RecoveryService;
import com.tracker.server.util.DateTimeUtil;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "tracker.legacy-recovery.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DeviceStatusScheduler {

    private final DeviceRepository repository;
    private final RecoveryService recoveryService;

    @Scheduled(fixedDelay = 30000)
    public void checkOffline() {

        LocalDateTime limit =
                DateTimeUtil.now()
                        .minusSeconds(20);

        repository.findAll()
                .forEach(device -> {

                    if (device.getLastSeen() == null) {
                        return;
                    }

                    if (device.getLastSeen().isBefore(limit)) {

                        device.setOnline(false);

                        repository.save(device);
                        recoveryService.recoveryProcess(device.getId());
                        recoveryService.recoveryWindow(device.getId());
                        recoveryService.recoveryIdle(device.getId());
                        recoveryService.recoverySession(device.getId());
                    }

                });

    }
}
