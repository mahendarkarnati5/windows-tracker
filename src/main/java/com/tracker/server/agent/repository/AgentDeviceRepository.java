package com.tracker.server.agent.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tracker.server.agent.entity.AgentDevice;

public interface AgentDeviceRepository extends JpaRepository<AgentDevice, Long> {
    Optional<AgentDevice> findByDeviceUuid(String deviceUuid);
    Optional<AgentDevice> findByDeviceUuidAndUserId(String deviceUuid, Long userId);
    List<AgentDevice> findByLastSeenAtBefore(LocalDateTime cutoff);
}
