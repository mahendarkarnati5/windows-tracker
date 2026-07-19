package com.tracker.server.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.tracker.server.entity.ActiveWindowActivity;
import com.tracker.server.entity.Device;
import com.tracker.server.repository.ActiveWindowActivityRepository;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.util.DateTimeUtil;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActiveWindowActivityService {
	private final ActiveWindowActivityRepository repository;
	private final DeviceRepository deviceRepository;

	@Transactional
	public ActiveWindowActivity create(Long deviceId,
	                                   ActiveWindowActivity activity) {

//	    // Same offlineId already exists
//	    Optional<ActiveWindowActivity> byOffline =
//	            repository.findByOfflineId(activity.getOfflineId());
//
//	    if (byOffline.isPresent()) {
//	        return byOffline.get();
//	    }
//
//	    // Same RUNNING window already exists
//	    Optional<ActiveWindowActivity> running =
//	            repository.findFirstByDeviceIdAndWindowTitleAndStatusOrderByStartTimeDesc(
//	                    deviceId,
//	                    activity.getWindowTitle(),
//	                    "RUNNING");
//
//	    if (running.isPresent()) {
//	        return running.get();
//	    }
		repository.closeRunning(deviceId, DateTimeUtil.now());
	    Device device=deviceRepository.findById(deviceId).orElseThrow(()->new RuntimeException("device not found with : "+deviceId));

	    activity.setDevice(device);;

	    return repository.save(activity);
	}
}
