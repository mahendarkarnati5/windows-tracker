package com.tracker.server.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tracker.server.util.DateTimeUtil;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;
@Column(nullable = true)
    @JsonIgnore
    private String password;

    private String role;
    private LocalDateTime createdAt;
    @PrePersist
    public void onCreate() {
    	this.createdAt=DateTimeUtil.now();
    }
}
