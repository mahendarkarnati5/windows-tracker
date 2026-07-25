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

    Optional<ProcessActivity> findFirstByDeviceIdAndNaturalKeyOrderByIdDesc(
            Long deviceId, String naturalKey);

    Optional<ProcessActivity> findFirstByDeviceIdAndPidAndProcessNameIgnoreCaseAndStartTimeAndEndTimeIsNullOrderByIdDesc(
            Long deviceId,
            Long pid,
            String processName,
            LocalDateTime startTime);

    Optional<ProcessActivity> findFirstByDeviceIdAndPidAndProcessNameIgnoreCaseAndStartTimeOrderByIdDesc(
            Long deviceId, Long pid, String processName, LocalDateTime startTime);

    Optional<ProcessActivity> findFirstByDeviceIdAndPidAndStartTimeOrderByIdDesc(
            Long deviceId, Long pid, LocalDateTime startTime);

    List<ProcessActivity> findByDeviceIdAndPidAndStartTimeOrderByIdDesc(
            Long deviceId, Long pid, LocalDateTime startTime);

    List<ProcessActivity> findByDeviceIdAndPidAndStartTimeBetweenOrderByIdDesc(
            Long deviceId,
            Long pid,
            LocalDateTime fromStart,
            LocalDateTime toStart);

    @Query("""
           select p from ProcessActivity p
           where p.device.id = :deviceId
             and p.pid = :pid
             and upper(p.status) = upper(:status)
           order by p.startTime asc, p.id asc
           """)
    List<ProcessActivity> findByDeviceIdAndPidAndStatusOrderByStartTimeAscIdAsc(
            @Param("deviceId") Long deviceId,
            @Param("pid") Long pid,
            @Param("status") String status);

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

    @Query("""
           select p.device.id, p.pid, p.startTime
           from ProcessActivity p
           where p.device is not null and p.pid is not null and p.startTime is not null
           group by p.device.id, p.pid, p.startTime
           having count(p.id) > 1
           """)
    List<Object[]> findDuplicateNaturalKeys();

    List<ProcessActivity> findByDeviceIdAndPidOrderByStartTimeAscIdAsc(
            Long deviceId, Long pid);

    @Query("""
           select p.device.id, p.pid
           from ProcessActivity p
           where p.device is not null and p.pid is not null
             and upper(p.status) = 'RUNNING'
           group by p.device.id, p.pid
           having count(p.id) > 1
           """)
    List<Object[]> findDuplicateRunningKeys();

    @Query("""
           select distinct p.device.id, p.pid
           from ProcessActivity p
           where p.device is not null and p.pid is not null
             and upper(p.status) = 'RUNNING'
             and exists (
                 select other.id from ProcessActivity other
                 where other.device.id = p.device.id
                   and other.pid = p.pid
                   and other.id <> p.id
             )
           """)
    List<Object[]> findRunningKeysWithOtherRows();

}