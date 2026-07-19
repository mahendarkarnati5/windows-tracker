package com.tracker.server.agent.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tracker.server.agent.dto.AgentEnrollmentRequest;
import com.tracker.server.agent.dto.AgentEnrollmentResponse;
import com.tracker.server.agent.dto.AgentSyncRequest;
import com.tracker.server.agent.dto.AgentSyncResponse;
import com.tracker.server.agent.service.AgentDeviceService;
import com.tracker.server.agent.service.AgentSyncService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentDeviceService deviceService;
    private final AgentSyncService syncService;

    @PostMapping("/devices/enroll")
    public AgentEnrollmentResponse enroll(
            Authentication authentication,
            @Valid @RequestBody AgentEnrollmentRequest request,
            HttpServletRequest servletRequest) {
        return deviceService.enroll(
                authentication.getName(), request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/devices/{deviceUuid}/heartbeat")
    public ResponseEntity<Void> heartbeat(
            Authentication authentication,
            @PathVariable String deviceUuid,
            HttpServletRequest servletRequest) {
        deviceService.heartbeat(
                authentication.getName(), deviceUuid, servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/devices/{deviceUuid}/activities")
    public AgentSyncResponse synchronize(
            Authentication authentication,
            @PathVariable String deviceUuid,
            @Valid @RequestBody AgentSyncRequest request) {
        return syncService.synchronize(authentication.getName(), deviceUuid, request);
    }
}
