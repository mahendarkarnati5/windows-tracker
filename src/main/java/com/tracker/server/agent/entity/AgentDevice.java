package com.tracker.server.agent.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "agent_devices")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_uuid", nullable = false, unique = true, length = 36)
    private String deviceUuid;

    @Column(name = "legacy_device_id", nullable = false)
    private Long legacyDeviceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private String machineName;
    private String osName;
    private String agentVersion;
    private String lastIpAddress;
    private LocalDateTime lastSeenAt;

    @Column(name = "current_session_uuid", length = 36)
    private String currentSessionUuid;

    @Column(name = "current_session_started_at")
    private LocalDateTime currentSessionStartedAt;

    @Column(name = "current_session_sequence")
    private Long currentSessionSequence;

    @Column(name = "lifecycle_state", length = 32)
    private String lifecycleState;

    @Column(name = "last_lifecycle_at")
    private LocalDateTime lastLifecycleAt;

    @Column(name = "credential_hash", length = 64)
    private String credentialHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
