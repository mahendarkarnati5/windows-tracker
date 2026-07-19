package com.tracker.server.agent.entity;

import java.time.LocalDateTime;

import com.tracker.server.agent.model.ActivityKind;
import com.tracker.server.agent.model.ActivityState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "agent_activities")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentActivity {

    @Id
    @Column(name = "record_uuid", length = 36, nullable = false)
    private String recordUuid;

    @Column(name = "device_uuid", length = 36, nullable = false)
    private String deviceUuid;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ActivityKind kind;

    @Column(nullable = false)
    private long revision;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;
    private Long durationMillis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ActivityState state;

    @Column(length = 64)
    private String closeReason;

    private Long processId;

    @Column(length = 512)
    private String processName;

    @Column(length = 1000)
    private String windowTitle;

    private Long legacyRecordId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
