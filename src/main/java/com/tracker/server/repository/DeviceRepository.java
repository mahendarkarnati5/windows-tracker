package com.tracker.server.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.tracker.server.entity.Device;

import jakarta.transaction.Transactional;

public interface DeviceRepository
        extends JpaRepository<Device, Long> {
	Optional<Device> findById(Long id);
	List<Device> findByUserId(Long userId);
	 Optional<Device> findByMacAddressAndUserId(
	            String macAddress,
	            Long userId);

	 List<Device> findByStatus(String status);
	 
	 @Modifying
	 @Transactional
	 @Query("""
	 UPDATE Device d
	 SET d.lastSeen = :time,
	     d.online = true
	 WHERE d.id = :deviceId
	 """)
	 void updateHeartbeat(
	         Long deviceId,
	         LocalDateTime time);
}