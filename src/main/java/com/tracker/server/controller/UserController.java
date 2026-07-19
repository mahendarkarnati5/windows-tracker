package com.tracker.server.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tracker.server.dto.UserDashboardDto;
import com.tracker.server.entity.ActiveWindowActivity;
import com.tracker.server.entity.DeviceSession;
import com.tracker.server.entity.IdleActivity;
import com.tracker.server.entity.ProcessActivity;
import com.tracker.server.entity.User;
import com.tracker.server.repository.DeviceSessionRepository;
import com.tracker.server.repository.ProcessActivityRepository;
import com.tracker.server.repository.UserRepository;
import com.tracker.server.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final ProcessActivityRepository processRepository;
    private final DeviceSessionRepository deviceSessionRepository;

    @GetMapping("/my-processes")
    public List<ProcessActivity> myProcesses(
            Authentication authentication) {

        String username =
                authentication.getName();
        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow();
        return processRepository
                .findByUser_IdOrderByIdDesc(
                        user.getId());
    }

    @GetMapping("/me/stats/{userId}")
    public Map<String, Long> myStats(
            @PathVariable Long userId,
            Authentication authentication) {
        User current = userRepository.findByUsername(authentication.getName())
                .orElseThrow();
        boolean administrator = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!current.getId().equals(userId) && !administrator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return userService.getUserStats(
                userId);
    }
    
    @GetMapping("/process-summary")
    public List<ProcessActivity> processes(
            Authentication authentication) {

        return userService.getUserProcesses(
                authentication.getName());
    }

    @GetMapping("/windows")
    public List<ActiveWindowActivity> windows(
            Authentication authentication) {

        return userService.getUserWindows(
                authentication.getName());
    }

    @GetMapping("/idle")
    public List<IdleActivity> idle(
            Authentication authentication) {

        return userService.getUserIdle(
                authentication.getName());
    }
    
    
    @GetMapping("/productivity")
    public Map<String,Object>
    productivity(
            Authentication authentication) {

        return userService
                .getProductivity(
                        authentication.getName());
    }
    
    
    @GetMapping("/my-sessions")
    public List<DeviceSession> mySessions(
            Authentication auth) {

        String username =
                auth.getName();

        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow();

        return deviceSessionRepository
                .findByUserIdOrderByIdDesc(
                        user.getId());
    }
    
    @GetMapping("/dashboard")
    public UserDashboardDto dashboard(
            Authentication auth) {

        return userService.getDashboard(
                auth.getName());
    }
    
    
}
