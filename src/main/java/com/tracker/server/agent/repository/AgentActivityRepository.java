package com.tracker.server.agent.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.tracker.server.agent.entity.AgentActivity;

import jakarta.persistence.LockModeType;

public interface AgentActivityRepository extends JpaRepository<AgentActivity, String> {
    List<AgentActivity> findByDeviceUuidOrderByStartedAtAsc(String deviceUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AgentActivity a where a.recordUuid = :recordUuid")
    java.util.Optional<AgentActivity> findByRecordUuidForUpdate(String recordUuid);
}
