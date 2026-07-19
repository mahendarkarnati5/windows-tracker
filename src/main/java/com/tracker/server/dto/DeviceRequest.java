package com.tracker.server.dto;

import lombok.Data;

@Data
public class DeviceRequest {

    private String macAddress;
    private String machineName;
    private String osName;
    private String lastIpAddress;
}