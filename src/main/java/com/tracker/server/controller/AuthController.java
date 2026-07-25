package com.tracker.server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tracker.server.dto.AdminLoginRequest;
import com.tracker.server.dto.LoginRequest;
import com.tracker.server.dto.LoginResponse;
import com.tracker.server.dto.RegisterRequest;
import com.tracker.server.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/admin/register")
    public String adminRegister(
            @Valid @RequestBody AdminLoginRequest request) {

        return authService.adminRegister(
                request);
    }
    
    

    @PostMapping("/register")
    public ResponseEntity<String> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }
    
    
    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request) {

        return authService.login(request);
    }
    
    
    
    @PostMapping("/admin")
    public LoginResponse adminLogin(
            @Valid @RequestBody AdminLoginRequest request) {

        return authService.adminLogin(request);
    }


    
    
}
