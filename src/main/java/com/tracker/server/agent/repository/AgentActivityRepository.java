package com.tracker.server.agent.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tracker.server.agent.entity.AgentActivity;
import com.tracker.server.agent.model.ActivityState;

import jakarta.persistence.LockModeType;

public interface AgentActivityRepository extends JpaRepository<AgentActivity, String> {
    List<AgentActivity> findByDeviceUuidOrderByStartedAtAsc(String deviceUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AgentActivity a where a.recordUuid = :recordUuid")
    Optional<AgentActivity> findByRecordUuidForUpdate(@Param("recordUuid") String recordUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select a from AgentActivity a
           where a.deviceUuid = :deviceUuid and a.state = :state
           order by a.startedAt
           """)
    List<AgentActivity> findByDeviceUuidAndStateForUpdate(
            @Param("deviceUuid") String deviceUuid,
            @Param("state") ActivityState state);
}
