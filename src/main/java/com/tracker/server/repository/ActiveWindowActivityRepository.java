package com.tracker.server.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tracker.server.entity.ActiveWindowActivity;

import jakarta.transaction.Transactional;

@Repository
public interface ActiveWindowActivityRepository
        extends JpaRepository<ActiveWindowActivity, Long>, JpaSpecificationExecutor<ActiveWindowActivity> {

    List<ActiveWindowActivity> findByDeviceIdOrderByIdDesc(Long deviceId);

    Optional<ActiveWindowActivity> findByOfflineId(String offlineId);

    List<ActiveWindowActivity> findByDeviceIdAndStatus(Long deviceId, String status);

    Optional<ActiveWindowActivity> findFirstByDeviceIdAndNaturalKeyOrderByIdDesc(
            Long deviceId, String naturalKey);

    Optional<ActiveWindowActivity> findFirstByDeviceIdAndPidAndProcessNameIgnoreCaseAndWindowTitleAndStartTimeAndEndTimeIsNullOrderByIdDesc(
            Long deviceId,
            Long pid,
            String processName,
            String windowTitle,
            LocalDateTime startTime);

    Optional<ActiveWindowActivity> findFirstByDeviceIdAndPidAndProcessNameIgnoreCaseAndWindowTitleAndStartTimeOrderByIdDesc(
            Long deviceId,
            Long pid,
            String processName,
            String windowTitle,
            LocalDateTime startTime);

    Optional<ActiveWindowActivity> findFirstByDeviceIdAndWindowTitleAndStatusOrderByStartTimeDesc(
            Long deviceId, String windowTitle, String status);

    Optional<ActiveWindowActivity> findFirstByDeviceIdAndStatusOrderByStartTimeDesc(
            Long deviceId, String status);

    List<ActiveWindowActivity> findByDeviceIdAndStartTimeAndWindowTitleOrderByIdDesc(
            Long deviceId, LocalDateTime startTime, String windowTitle);

    @Modifying
    @Transactional
    @Query("""
           update ActiveWindowActivity a
           set a.status = 'CLOSED', a.endTime = :end
           where a.device.id = :deviceId and a.status = 'RUNNING'
           """)
    void closeRunning(@Param("deviceId") Long deviceId, @Param("end") LocalDateTime end);

    @Query("""
           select coalesce(sum(coalesce(a.durationSeconds, 0)), 0)
           from ActiveWindowActivity a
           where a.device.id = :deviceId
             and (:status is null or upper(a.status) = :status)
             and (:search is null or lower(a.windowTitle) like concat('%', :search, '%'))
             and (:fromTime is null or a.endTime is null or a.endTime >= :fromTime)
             and (:toTime is null or a.startTime <= :toTime)
           """)
    Long sumFilteredDuration(
            @Param("deviceId") Long deviceId,
            @Param("status") String status,
            @Param("search") String search,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime);
    @Query("""
           select distinct a.device.id from ActiveWindowActivity a
           where a.device is not null and upper(a.status) = 'RUNNING'
           """)
    List<Long> findDeviceIdsWithRunningRows();

    @Query("""
           select a.device.id, a.startTime, a.windowTitle
           from ActiveWindowActivity a
           where a.device is not null and a.startTime is not null and a.windowTitle is not null
           group by a.device.id, a.startTime, a.windowTitle
           having count(a.id) > 1
           """)
    List<Object[]> findDuplicateNaturalKeys();

}