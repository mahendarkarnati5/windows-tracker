package com.tracker.server.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 100)
    private String username;

//    @NotBlank
//    @Size(min = 8, max = 200)
//    private String password;

//    private String role;
}
