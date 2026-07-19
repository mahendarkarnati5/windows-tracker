package com.tracker.server.controller;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tracker.server.dto.OfflineSessionRequest;
import com.tracker.server.entity.Device;
import com.tracker.server.entity.DeviceSession;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.repository.DeviceSessionRepository;
import com.tracker.server.util.DateTimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
@Slf4j
public class DeviceSessionController {

    private final DeviceSessionRepository repository;
    private final DeviceRepository deviceRepository;

//    @PostMapping("/start/{deviceId}")
//    public void start(
//            @PathVariable Long deviceId) {
//    	   if (repository.findTopByDeviceIdAndStatusOrderByIdDesc(
//    	            deviceId,
//    	            "RUNNING").isPresent()) {
//
//    	        return;
//    	    }
//        Device device =
//                deviceRepository
//                        .findById(deviceId)
//                        .orElseThrow();
//
//        DeviceSession session =
//                DeviceSession.builder()
//                        .device(device).user(device.getUser())
//                        
//                        .startupTime(
//                        		DateTimeUtil.now())
//                        .status("RUNNING")
//                        .build();
//
//        repository.save(session);
//    }
    
    
    @PostMapping("/start/{deviceId}")
    public DeviceSession start(
            @PathVariable Long deviceId) {

        Device device =
                deviceRepository.findById(deviceId)
                        .orElseThrow();

        return repository.save(
                DeviceSession.builder()
                        .device(device)
                        .user(device.getUser())
                        .startupTime(DateTimeUtil.now())
                        .status("RUNNING")
                        .build());
    }

//    @PostMapping("/end/{deviceId}")
//    public void end(@PathVariable Long deviceId) {
//    	log.info("SESSION END API HIT device={}", deviceId);
//        repository.findTopByDeviceIdAndStatusOrderByIdDesc(
//                deviceId,
//                "RUNNING")
//                .ifPresent(session -> {
//
//                    LocalDateTime end = DateTimeUtil.now();
//
//                    session.setShutdownTime(end);
//
//                    session.setSessionDurationSeconds(
//                            Duration.between(
//                                    session.getStartupTime(),
//                                    end)
//                                    .getSeconds());
//
//                    session.setStatus("CLOSED");
//
//                    repository.save(session);
//                });
//    }
    
    
    
    
    
    
    
    
    @PostMapping("/end/{sessionId}")
    public void end(@PathVariable Long sessionId) {

        DeviceSession session =
                repository.findById(sessionId)
                        .orElseThrow();
        
        if (session.getShutdownTime() != null) return;

        LocalDateTime end = DateTimeUtil.now();

        session.setShutdownTime(end);

        session.setSessionDurationSeconds(
                Duration.between(
                        session.getStartupTime(),
                        end)
                        .getSeconds());

        session.setStatus("CLOSED");

        repository.save(session);
    }

//
//    @PostMapping("/offline")
//public ResponseEntity<?> saveOfflineSession(
//        @RequestBody OfflineSessionRequest request) {
//
//    DeviceSession session = new DeviceSession();
//    Device device=deviceRepository.findById(request.getDeviceId()).orElseThrow(()->new RuntimeException("device not found with: "+request.getDeviceId()));
//    session.setDevice(device);
//
//    session.setStartupTime(request.getStartTime());
//
//    session.setShutdownTime(request.getEndTime());
//
//    repository.save(session);
//
//    return ResponseEntity.ok().build();
//}
    
    
    @PostMapping("/offline")
    public ResponseEntity<?> saveOfflineSession(
            @RequestBody OfflineSessionRequest request) {

        DeviceSession session = new DeviceSession();

        Device device=deviceRepository.findById(request.getDeviceId()).orElseThrow(()->new RuntimeException("device not found with: "+request.getDeviceId()));
        session.setDevice(device);

        session.setStartupTime(request.getStartTime());

        session.setShutdownTime(request.getEndTime());

        session.setStatus(
                request.getEndTime() == null
                        ? "RUNNING"
                        : "CLOSED");
        session.setSessionDurationSeconds(
                Duration.between(
                        session.getStartupTime(),
                        request.getEndTime())
                        .getSeconds());

        repository.save(session);
        
        

        return ResponseEntity.ok().build();
    }
    
    
    
    @PostMapping
    public void offlineSession(
            @RequestBody OfflineSessionRequest request) {

        DeviceSession session = new DeviceSession();

        Device device=deviceRepository.findById(request.getDeviceId()).orElseThrow(()->new RuntimeException("device not found with: "+request.getDeviceId()));
        session.setDevice(device);

        session.setStartupTime(request.getStartTime());

        session.setShutdownTime(request.getEndTime());

        session.setStatus(
                request.getEndTime() == null
                        ? "RUNNING"
                        : "CLOSED");
        session.setSessionDurationSeconds(
                Duration.between(
                        session.getStartupTime(),
                        request.getEndTime())
                        .getSeconds());

        repository.save(session);
        
    }
}