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
        extends JpaRepository<ProcessActivity, Long>, JpaSpecificationExecutor<ProcessActivity> {

    List<ProcessActivity> findByDeviceId(Long deviceId);

    List<ProcessActivity> findTop10ByOrderByIdDesc();

    @Query("""
           select p.processName, sum(p.durationSeconds)
           from ProcessActivity p
           where p.device.id = :deviceId
             and p.status = 'CLOSED'
           group by p.processName
           order by sum(p.durationSeconds) desc
           """)
    List<Object[]> getTopApplications(Long deviceId);

    List<ProcessActivity> findByUser_IdOrderByIdDesc(Long userId);

    @Query("""
           select coalesce(sum(p.durationSeconds), 0)
           from ProcessActivity p
           where p.user.id = :userId
           """)
    Long getTotalActiveTime(@Param("userId") Long userId);

    @Query("""
           select new com.tracker.server.dto.AppUsageDto(
               p.processName,
               coalesce(sum(p.durationSeconds), 0)
           )
           from ProcessActivity p
           where p.user.id = :userId
           group by p.processName
           order by sum(p.durationSeconds) desc
           """)
    List<AppUsageDto> topApps(@Param("userId") Long userId);

    List<ProcessActivity> findByDeviceIdAndStatus(Long deviceId, String status);

    Optional<ProcessActivity> findByDeviceIdAndPidAndStartTime(
            Long deviceId, Long pid, LocalDateTime startTime);

    Optional<ProcessActivity> findFirstByDeviceIdAndPidAndStatusOrderByIdDesc(
            Long deviceId, Long pid, String status);

    long countByDeviceIdAndStatus(Long deviceId, String status);

    Optional<ProcessActivity> findFirstByDeviceIdAndStatusOrderByStartTimeDesc(
            Long deviceId, String status);

    @Query("""
           select coalesce(sum(coalesce(p.durationSeconds, 0)), 0)
           from ProcessActivity p
           where p.device.id = :deviceId
             and (:status is null or upper(p.status) = :status)
             and (:search is null or lower(p.processName) like concat('%', :search, '%'))
             and (:fromTime is null or p.endTime is null or p.endTime >= :fromTime)
             and (:toTime is null or p.startTime <= :toTime)
           """)
    Long sumFilteredDuration(
            @Param("deviceId") Long deviceId,
            @Param("status") String status,
            @Param("search") String search,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime);
    @Query("""
           select distinct p.device.id from ProcessActivity p
           where p.device is not null and upper(p.status) = 'RUNNING'
           """)
    List<Long> findDeviceIdsWithRunningRows();

}