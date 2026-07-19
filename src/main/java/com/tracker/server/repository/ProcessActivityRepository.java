package com.tracker.server.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tracker.server.dto.AppUsageDto;
import com.tracker.server.entity.ProcessActivity;

public interface ProcessActivityRepository
extends JpaRepository<ProcessActivity, Long>,
JpaSpecificationExecutor<ProcessActivity> {
	
	  List<ProcessActivity> findByDeviceId(Long deviceId);
	  List<ProcessActivity>
	  findTop10ByOrderByIdDesc();
	  
	  
	  @Query("""
			  SELECT p.processName,
			  SUM(p.durationSeconds)
			  FROM ProcessActivity p
			  WHERE p.device.id = :deviceId
			  AND p.status = 'CLOSED'
			  GROUP BY p.processName
			  ORDER BY SUM(p.durationSeconds) DESC
			  """)
			  List<Object[]> getTopApplications(
			          Long deviceId);
			  
			  
//			  List<ProcessActivity>
//			  findByUserIdOrderByIdDesc(
//			          Long userId);
			  List<ProcessActivity>
			  findByUser_IdOrderByIdDesc(Long userId);
			  
			  @Query("""
				       SELECT COALESCE(
				           SUM(p.durationSeconds),
				           0
				       )
				       FROM ProcessActivity p
				       WHERE p.user.id = :userId
				       """)
				Long getTotalActiveTime(
				        @Param("userId")
				        Long userId);
			  @Query("""
				       SELECT new com.tracker.server.dto.AppUsageDto(
				           p.processName,
				           COALESCE(
				               SUM(p.durationSeconds),
				               0
				           )
				       )
				       FROM ProcessActivity p
				       WHERE p.user.id = :userId
				       GROUP BY p.processName
				       ORDER BY SUM(p.durationSeconds) DESC
				       """)
				List<AppUsageDto> topApps(
				        @Param("userId")
				        Long userId);
			  
			  
			  List<ProcessActivity> findByDeviceIdAndStatus(
				        Long deviceId,
				        String status);
			  
			    Optional<ProcessActivity> findByDeviceIdAndPidAndStartTime(
			            Long deviceId,
			            Long pid,
			            LocalDateTime startTime);
			    
			    
			    Optional<ProcessActivity> findFirstByDeviceIdAndPidAndStatusOrderByIdDesc(
			            Long deviceId,
			            Long pid,
			            String status);
			  
			  
			  
}