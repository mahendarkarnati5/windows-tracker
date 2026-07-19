package com.tracker.server.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tracker.server.entity.Device;
import com.tracker.server.entity.IdleActivity;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.repository.IdleActivityRepository;
import com.tracker.server.service.RecoveryService;
import com.tracker.server.util.DateTimeUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/idle")
@RequiredArgsConstructor
public class IdleActivityController {

    private final IdleActivityRepository repository;
    private final DeviceRepository deviceRepository;
    private final RecoveryService recoveryService;

    @PostMapping("/start/{deviceId}")
    public IdleActivity save(
            @PathVariable Long deviceId,
            @RequestBody IdleActivity activity) {

        Device device =
                deviceRepository.findById(deviceId)
                        .orElseThrow();

        activity.setDevice(device);
        activity.setUser(device.getUser());

        return repository.save(activity);
    }
    
    @PutMapping("/end/{id}")
    public IdleActivity endIdle(
            @PathVariable Long id,
            @RequestBody IdleActivity activity) {

    	IdleActivity db =
    			repository.findById(id)
                        .orElseThrow();

        db.setIdleEnd(activity.getIdleEnd());
        db.setIdleSeconds(activity.getIdleSeconds());
        db.setStatus("CLOSED");

        return repository.save(db);
    }
    
    
    @PostMapping("/recover/{deviceId}")
    public void recoverIdleActivities(
            @PathVariable Long deviceId) {

        recoveryService.recoveryIdle(deviceId);
    }
}