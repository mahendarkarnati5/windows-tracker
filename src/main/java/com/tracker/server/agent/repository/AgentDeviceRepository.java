package com.tracker.server.agent.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tracker.server.agent.entity.AgentDevice;

import jakarta.persistence.LockModeType;

public interface AgentDeviceRepository extends JpaRepository<AgentDevice, Long> {
    Optional<AgentDevice> findByDeviceUuid(String deviceUuid);
    Optional<AgentDevice> findByDeviceUuidAndUserId(String deviceUuid, Long userId);
    List<AgentDevice> findByLastSeenAtBefore(LocalDateTime cutoff);
    List<AgentDevice> findByLastSeenAtBeforeAndLifecycleState(
            LocalDateTime cutoff, String lifecycleState);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from AgentDevice d where d.id = :id")
    Optional<AgentDevice> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select d from AgentDevice d
           where d.deviceUuid = :deviceUuid and d.userId = :userId
           """)
    Optional<AgentDevice> findOwnedForUpdate(
            @Param("deviceUuid") String deviceUuid,
            @Param("userId") Long userId);
}
