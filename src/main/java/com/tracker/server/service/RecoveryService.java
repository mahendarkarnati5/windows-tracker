package com.tracker.server.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tracker.server.entity.ActiveWindowActivity;
import com.tracker.server.entity.Device;
import com.tracker.server.entity.DeviceSession;
import com.tracker.server.entity.IdleActivity;
import com.tracker.server.entity.ProcessActivity;
import com.tracker.server.repository.ActiveWindowActivityRepository;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.repository.DeviceSessionRepository;
import com.tracker.server.repository.IdleActivityRepository;
import com.tracker.server.repository.ProcessActivityRepository;
import com.tracker.server.util.DateTimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecoveryService {
	private final ActiveWindowActivityRepository activeWindowActivityRepository;
	public final DeviceRepository deviceRepository;
	private final IdleActivityRepository idleActivityRepository;
	private final ProcessActivityRepository processRepository;
	private final DeviceSessionRepository deviceSessionRepository;
	
	public void recoveryWindow(Long deviceId) {
		List<ActiveWindowActivity> running =
				activeWindowActivityRepository.findByDeviceIdAndStatus(
                        deviceId,
                        "RUNNING");

        Device device =
                deviceRepository.findById(deviceId)
                        .orElseThrow();

        LocalDateTime crashTime = device.getLastSeen();

        if (crashTime == null) {
            crashTime = DateTimeUtil.now();
        }

        for (ActiveWindowActivity w : running) {

            w.setEndTime(crashTime);

            w.setDurationSeconds(
                    Duration.between(
                            w.getStartTime(),
                            crashTime).getSeconds());
            if(Duration.between(
                            w.getStartTime(),
                            crashTime).getSeconds()>0) {
            w.setStatus("CLOSED");
            }
            else {
            	w.setStatus("INTERRUPTED");
            }
        }

        activeWindowActivityRepository.saveAll(running);
	}
	
	
	public void recoveryIdle(Long deviceId) {
		List<IdleActivity> running =
				idleActivityRepository.findByDeviceIdAndStatus(
                        deviceId,
                        "RUNNING");

        Device device =
                deviceRepository.findById(deviceId)
                        .orElseThrow();

        LocalDateTime crashTime = device.getLastSeen();

        if (crashTime == null) {
            crashTime = DateTimeUtil.now();
        }

        for (IdleActivity idle : running) {

            idle.setIdleEnd(crashTime);

            idle.setIdleSeconds(
                    Duration.between(
                            idle.getIdleStart(),
                            crashTime)
                            .getSeconds());

            if(Duration.between(
            		idle.getIdleStart(),
                    crashTime).getSeconds()>0) {
            	idle.setStatus("CLOSED");
    }
    else {
    	idle.setStatus("INTERRUPTED");
    }
        }

        idleActivityRepository.saveAll(running);
	}
	
	public void recoveryProcess(Long deviceId) {
		List<ProcessActivity> running =
 			   processRepository.findByDeviceIdAndStatus(
 	                    deviceId,
 	                    "RUNNING");

 	   Device device =
 		        deviceRepository.findById(deviceId)
 		                .orElseThrow();

 		LocalDateTime crashTime = device.getLastSeen();

 		if (crashTime == null) {
 		    crashTime = DateTimeUtil.now();
 		}

 	    for (ProcessActivity p : running) {

 	        p.setEndTime(crashTime);

 	        p.setDurationSeconds(
 	                Duration.between(
 	                        p.getStartTime(),
 	                        crashTime)
 	                        .getSeconds());

 	       if(Duration.between(
                   p.getStartTime(),
                   crashTime).getSeconds()>0) {
   p.setStatus("CLOSED");
   }
   else {
   	p.setStatus("INTERRUPTED");
   }
 	    }

 	    processRepository.saveAll(running);
	}
	
	public void recoverySession(Long deviceId) {
        List<DeviceSession> sessions =
        		deviceSessionRepository.findByDeviceIdAndStatus(deviceId,"RUNNING");

        for (DeviceSession session : sessions) {

            LocalDateTime end = DateTimeUtil.now();

            session.setShutdownTime(end);

            session.setSessionDurationSeconds(
                    Duration.between(
                            session.getStartupTime(),
                            end)
                            .getSeconds());

            if(Duration.between(
            		session.getStartupTime(),
                    end)
                    .getSeconds()>0) {
            	session.setStatus("CLOSED");
    }
    else {
    	session.setStatus("INTERRUPTED");
    }

            deviceSessionRepository.save(session);

            log.info(
                    "Recovered Session {}",
                    session.getId());
        }
	}

}
