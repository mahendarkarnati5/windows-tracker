package com.tracker.server.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tracker.server.entity.DeviceSession;

@Repository
public interface DeviceSessionRepository
        extends JpaRepository<DeviceSession, Long>, JpaSpecificationExecutor<DeviceSession> {

    List<DeviceSession> findByUserId(Long userId);

    Optional<DeviceSession> findTopByDeviceIdAndStatusOrderByIdDesc(Long deviceId, String status);

    List<DeviceSession> findByDeviceId(Long deviceId);

    List<DeviceSession> findByDeviceIdOrderByIdDesc(Long deviceId);

    List<DeviceSession> findByUserIdOrderByIdDesc(Long userId);

    List<DeviceSession> findByStatus(String status);

    List<DeviceSession> findByDeviceIdAndStatus(Long deviceId, String status);

    Optional<DeviceSession> findFirstByDeviceIdAndNaturalKeyOrderByIdDesc(
            Long deviceId, String naturalKey);

    Optional<DeviceSession> findFirstByDeviceIdAndStartupTimeAndShutdownTimeIsNullOrderByIdDesc(
            Long deviceId, LocalDateTime startupTime);

    Optional<DeviceSession> findTopByDeviceIdOrderByStartupTimeDesc(Long deviceId);

    List<DeviceSession> findByDeviceIdAndStartupTimeOrderByIdDesc(
            Long deviceId, LocalDateTime startupTime);

    @Query("""
           select s from DeviceSession s
           where s.id in (
               select max(s2.id) from DeviceSession s2
               where s2.device is not null
               group by s2.device.id
           )
           """)
    List<DeviceSession> findLatestForAllDevices();

    @Query("""
           select coalesce(sum(coalesce(s.sessionDurationSeconds, 0)), 0)
           from DeviceSession s
           where s.device.id = :deviceId
             and (:status is null or upper(s.status) = :status)
             and (:fromTime is null or s.shutdownTime is null or s.shutdownTime >= :fromTime)
             and (:toTime is null or s.startupTime <= :toTime)
           """)
    Long sumFilteredDuration(
            @Param("deviceId") Long deviceId,
            @Param("status") String status,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime);
    @Query("""
           select distinct s.device.id from DeviceSession s
           where s.device is not null and upper(s.status) = 'RUNNING'
           """)
    List<Long> findDeviceIdsWithRunningRows();

    @Query("""
           select s.device.id, s.startupTime
           from DeviceSession s
           where s.device is not null and s.startupTime is not null
           group by s.device.id, s.startupTime
           having count(s.id) > 1
           """)
    List<Object[]> findDuplicateNaturalKeys();

}