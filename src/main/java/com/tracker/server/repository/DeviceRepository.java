package com.tracker.server.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tracker.server.entity.Device;

import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

public interface DeviceRepository
        extends JpaRepository<Device, Long> {
	Optional<Device> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Device d where d.id = :id")
    Optional<Device> findByIdForUpdate(@Param("id") Long id);
	List<Device> findByUserId(Long userId);
	 Optional<Device> findByMacAddressAndUserId(
	            String macAddress,
	            Long userId);

	 List<Device> findByStatus(String status);
     List<Device> findByOnlineTrueAndLastSeenBefore(LocalDateTime cutoff);
	 
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