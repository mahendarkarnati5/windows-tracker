package com.tracker.server.agent.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tracker.server.agent.entity.AgentActivity;
import com.tracker.server.agent.model.ActivityKind;
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

    @Query("""
           select a from AgentActivity a
           where a.kind = :kind and a.legacyRecordId in :legacyRecordIds
           """)
    List<AgentActivity> findByKindAndLegacyRecordIdIn(
            @Param("kind") ActivityKind kind,
            @Param("legacyRecordIds") Collection<Long> legacyRecordIds);

    @Modifying
    @Query("""
           update AgentActivity a
           set a.legacyRecordId = :keeperId
           where a.kind = :kind and a.legacyRecordId = :duplicateId
           """)
    int repointLegacyRecord(
            @Param("kind") ActivityKind kind,
            @Param("duplicateId") Long duplicateId,
            @Param("keeperId") Long keeperId);

}
