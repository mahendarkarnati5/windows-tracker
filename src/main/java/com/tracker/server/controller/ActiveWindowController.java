package com.tracker.server.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tracker.server.entity.ActiveWindowActivity;
import com.tracker.server.entity.Device;
import com.tracker.server.repository.ActiveWindowActivityRepository;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.service.ActiveWindowActivityService;
import com.tracker.server.service.RecoveryService;
import com.tracker.server.util.DateTimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/window")
@RequiredArgsConstructor
@Slf4j
public class ActiveWindowController {
	private final RecoveryService recoveryService;
    private final ActiveWindowActivityRepository repository;
    private final DeviceRepository deviceRepository;
    private final ActiveWindowActivityService activeWindowActivityService;

//    @PostMapping("/{deviceId}")
//    public ActiveWindowActivity save(
//            @PathVariable Long deviceId,
//            @RequestBody ActiveWindowActivity activity) {
//
//        Device device =
//                deviceRepository.findById(deviceId)
//                        .orElseThrow();
//
//        activity.setDevice(device);
//
//        return repository.save(activity);
//    }
    
    
//    @PostMapping("/{deviceId}")
//    public ActiveWindowActivity save(
//            @PathVariable Long deviceId,
//            @RequestBody ActiveWindowActivity activity) {
//
//        Optional<ActiveWindowActivity> existing =
//        		repository.findByOfflineId(activity.getOfflineId());
//
//        if (existing.isPresent()) {
//
//        	ActiveWindowActivity db = existing.get();
//
//            db.setEndTime(activity.getEndTime());
//            db.setDurationSeconds(activity.getDurationSeconds());
//            db.setStatus(activity.getStatus());
//
//            return repository.save(db);
//        }
//
//        Device device =
//                deviceRepository.findById(deviceId)
//                        .orElseThrow();
//
//        activity.setDevice(device);
//
//        return repository.save(activity);
//    }
    
    
    @PostMapping("/{deviceId}")
    public ActiveWindowActivity save(
            @PathVariable Long deviceId,
            @RequestBody ActiveWindowActivity activity) {

        return activeWindowActivityService.create(deviceId, activity);
    }
    
    @PutMapping("/{id}")
    public ActiveWindowActivity update(
            @PathVariable Long id,
            @RequestBody ActiveWindowActivity request) {

        ActiveWindowActivity activity =
                repository.findById(id)
                        .orElseThrow();

        activity.setEndTime(request.getEndTime());
        activity.setDurationSeconds(request.getDurationSeconds());
        activity.setStatus(request.getStatus());

        return repository.save(activity);
    }
    
    
    @PostMapping("/recover/{deviceId}")
    public void recoverWindows(
            @PathVariable Long deviceId) {

        recoveryService.recoveryWindow(deviceId);
    }
}