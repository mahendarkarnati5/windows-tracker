package com.tracker.server.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tracker.server.entity.IdleActivity;

@Repository
public interface IdleActivityRepository
        extends JpaRepository<IdleActivity, Long>, JpaSpecificationExecutor<IdleActivity> {

    List<IdleActivity> findByDeviceIdOrderByIdDesc(Long deviceId);

    @Query("""
           select coalesce(sum(i.idleSeconds), 0)
           from IdleActivity i
           where i.user.id = :userId
           """)
    Long getTotalIdleTime(@Param("userId") Long userId);

    List<IdleActivity> findByDeviceIdAndStatus(Long deviceId, String status);

    Optional<IdleActivity> findFirstByDeviceIdAndStatusOrderByIdleStartDesc(
            Long deviceId, String status);

    @Query("""
           select coalesce(sum(coalesce(i.idleSeconds, 0)), 0)
           from IdleActivity i
           where i.device.id = :deviceId
             and (:status is null or upper(i.status) = :status)
             and (:fromTime is null or i.idleEnd is null or i.idleEnd >= :fromTime)
             and (:toTime is null or i.idleStart <= :toTime)
           """)
    Long sumFilteredDuration(
            @Param("deviceId") Long deviceId,
            @Param("status") String status,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime);
}
