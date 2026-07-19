package com.tracker.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminLoginRequest {
    @NotBlank
    @Size(min = 3, max = 100)
	private String username;

    @NotBlank
    @Size(min = 8, max = 200)
	private String password;
}
