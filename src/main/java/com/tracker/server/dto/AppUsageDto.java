package com.tracker.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AppUsageDto {

    private String name;

    private Long seconds;
}