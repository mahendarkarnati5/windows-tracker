package com.tracker.server.entity;

import java.time.LocalDateTime;

import com.tracker.server.util.DateTimeUtil;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String macAddress;

    private String machineName;

    private String osName;
    private String lastIpAddress;
    private LocalDateTime lastSeen;
    private String status;
    private LocalDateTime uninstalledAt;

    private boolean online;
    private LocalDateTime createdAt;
    @PrePersist
    public void onCreate() {
    	this.createdAt=DateTimeUtil.now();
    }

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}