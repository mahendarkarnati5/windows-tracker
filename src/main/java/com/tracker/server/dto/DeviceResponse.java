package com.tracker.server.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;
@Data
@Builder
public class DeviceResponse {

	
	 private Long id;

	    private String macAddress;

	    private String machineName;

	    private String osName;
	    private String lastIpAddress;
	    private LocalDateTime lastSeen;

	    private Long userId;
	    private String username;

	    private String status;
	    private boolean online;
	    private LocalDateTime uninstalledAt;
}
