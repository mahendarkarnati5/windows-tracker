package com.tracker.server.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.tracker.server.agent.entity.AgentDevice;
import com.tracker.server.agent.repository.AgentDeviceRepository;
import com.tracker.server.agent.service.AgentCredentialService;
import com.tracker.server.entity.Device;
import com.tracker.server.entity.User;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.repository.UserRepository;
import com.tracker.server.security.AgentCredentialFilter;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentCredentialFilterIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AgentCredentialService credentialService;
    @Autowired AgentDeviceRepository agentDeviceRepository;
    @Autowired DeviceRepository deviceRepository;
    @Autowired UserRepository userRepository;

    private AgentDevice agentDevice;
    private String deviceToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .username("filter-" + UUID.randomUUID())
                .password("unused")
                .role("USER")
                .build());
        Device legacy = deviceRepository.save(Device.builder()
                .macAddress("UUID:" + UUID.randomUUID())
                .machineName("filter-test")
                .osName("Windows")
                .status("ACTIVE")
                .online(true)
                .user(user)
                .build());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        agentDevice = AgentDevice.builder()
                .deviceUuid(UUID.randomUUID().toString())
                .legacyDeviceId(legacy.getId())
                .userId(user.getId())
                .createdAt(now)
                .updatedAt(now)
                .lastSeenAt(now)
                .build();
        deviceToken = credentialService.issue(agentDevice);
        agentDevice = agentDeviceRepository.save(agentDevice);
    }

    @Test
    void deviceCredentialAuthenticatesOnlyItsOwnAgentPath() throws Exception {
        String heartbeat = "/api/v1/agent/devices/"
                + agentDevice.getDeviceUuid() + "/heartbeat";

        mockMvc.perform(post(heartbeat)
                        .header(AgentCredentialFilter.DEVICE_HEADER, agentDevice.getDeviceUuid())
                        .header(AgentCredentialFilter.TOKEN_HEADER, deviceToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(heartbeat)
                        .header(AgentCredentialFilter.DEVICE_HEADER, agentDevice.getDeviceUuid())
                        .header(AgentCredentialFilter.TOKEN_HEADER, "wrong"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/agent/devices/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header(AgentCredentialFilter.DEVICE_HEADER, agentDevice.getDeviceUuid())
                        .header(AgentCredentialFilter.TOKEN_HEADER, deviceToken))
                .andExpect(status().isUnauthorized());
    }
}
