package com.tracker.server.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tracker.server.dto.DeviceRequest;
import com.tracker.server.entity.Device;
import com.tracker.server.entity.DeviceSession;
import com.tracker.server.entity.User;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.repository.DeviceSessionRepository;
import com.tracker.server.repository.UserRepository;
import com.tracker.server.util.DateTimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
@Slf4j
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final DeviceSessionRepository deviceSessionRepository;

    @PostMapping("/register/{userId}")
    public Device registerDevice(
            @PathVariable Long userId,
            @RequestBody DeviceRequest request) {
    	
    	 Optional<Device> existing =
    	            deviceRepository.findByMacAddressAndUserId(
    	                    request.getMacAddress(),
    	                    userId);

    	    if (existing.isPresent()) {

    	        Device existingDevice = existing.get();

    	        // Re-install after a previous uninstall: bring the device back to ACTIVE
    	        existingDevice.setStatus("ACTIVE");
    	        existingDevice.setOnline(true);
    	        existingDevice.setUninstalledAt(null);
    	        existingDevice.setLastSeen(DateTimeUtil.now());

    	        return deviceRepository.save(existingDevice);
    	    }

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                        new RuntimeException(
                                "User not found : " + userId));
        
        log.info("IP address is: {}", request.getLastIpAddress());

        Device device =
                Device.builder()
                        .macAddress(request.getMacAddress())
                        .machineName(request.getMachineName())
                        .osName(request.getOsName())
                        .lastIpAddress(request.getLastIpAddress())
                        .status("ACTIVE")
                        .online(true)
                        .lastSeen(DateTimeUtil.now())
                        .user(user)
                        .build();

        return deviceRepository.save(device);
    }
    
    @GetMapping("/sessions/{deviceId}")
    public List<DeviceSession> getSessions(
            @PathVariable Long deviceId) {
    	
    	log.info("Device sessions request for deviceId: {}", deviceId);

        return deviceSessionRepository.findByDeviceIdOrderByIdDesc(
                deviceId);
    }
    
//    @PostMapping("/heartbeat/{deviceId}")
//    public void heartbeat(@PathVariable Long deviceId) {
//
//        log.info("HEARTBEAT RECEIVED : {}", deviceId);
//
//        Device device =
//                deviceRepository.findById(deviceId)
//                        .orElseThrow();
//
//        device.setLastSeen(LocalDateTime.now());
//        device.setOnline(true);
//
//        deviceRepository.save(device);
//
//        log.info("Heartbeat Saved for deviceId: {}", deviceId);
//    }
    
    @PostMapping("/heartbeat/{deviceId}")
    public void heartbeat(@PathVariable Long deviceId) {

        log.info("HEARTBEAT RECEIVED : {}", deviceId);

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("Device not found: " + deviceId));

        device.setLastSeen(DateTimeUtil.now());
        device.setOnline(true);
        deviceRepository.save(device);

        log.info("Heartbeat Saved for deviceId: {}", deviceId);
    }
    
    
    @PostMapping("/uninstall/{deviceId}")
    public ResponseEntity<Void> uninstall(@PathVariable Long deviceId) {

        log.info("UNINSTALL NOTIFICATION RECEIVED : {}", deviceId);

        Optional<Device> deviceOpt = deviceRepository.findById(deviceId);

        if (deviceOpt.isEmpty()) {
            log.warn("Device not found for uninstall: {}", deviceId);
            return ResponseEntity.notFound().build();
        }

        Device device = deviceOpt.get();

        device.setStatus("UNINSTALLED");
        device.setOnline(false);
        device.setUninstalledAt(DateTimeUtil.now());

        deviceRepository.save(device);

        log.info("Device marked UNINSTALLED : {}", deviceId);

        return ResponseEntity.ok().build();
    }
    
}