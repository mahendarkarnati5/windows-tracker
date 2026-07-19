package com.tracker.server.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "active_window_activity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiveWindowActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 1000)
    private String windowTitle;

    private LocalDateTime startTime;
    
    @Column(name = "offline_id", unique = true)
    private String offlineId;

    private LocalDateTime endTime;

    private Long durationSeconds;

    private String status;

    @ManyToOne
    @JoinColumn(name = "device_id")
    private Device device;
    
//    @ManyToOne
//    @JoinColumn(name = "user_id")
//    private User user;
}