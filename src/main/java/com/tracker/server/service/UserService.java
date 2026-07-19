package com.tracker.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tracker.server.dto.AppUsageDto;
import com.tracker.server.dto.UserDashboardDto;
import com.tracker.server.entity.ActiveWindowActivity;
import com.tracker.server.entity.Device;
import com.tracker.server.entity.DeviceSession;
import com.tracker.server.entity.IdleActivity;
import com.tracker.server.entity.ProcessActivity;
import com.tracker.server.entity.User;
import com.tracker.server.repository.ActiveWindowActivityRepository;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.repository.DeviceSessionRepository;
import com.tracker.server.repository.IdleActivityRepository;
import com.tracker.server.repository.ProcessActivityRepository;
import com.tracker.server.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final DeviceRepository deviceRepository;
    private final ProcessActivityRepository processRepository;
    private final ActiveWindowActivityRepository windowRepository;
    private final IdleActivityRepository idleRepository;
    private final UserRepository userRepository;
    private final DeviceSessionRepository deviceSessionRepository;

    public Map<String, Long> getUserStats(
            Long userId) {

        Map<String, Long> stats =
                new HashMap<>();

        List<Device> devices =
                deviceRepository.findByUserId(
                        userId);

        long processCount = 0;
        long windowCount = 0;
        long idleCount = 0;

        for (Device device : devices) {

            processCount +=
                    processRepository
                            .findByDeviceId(
                                    device.getId())
                            .size();

            windowCount +=
                    windowRepository
                            .findByDeviceIdOrderByIdDesc(
                                    device.getId())
                            .size();

            idleCount +=
                    idleRepository
                            .findByDeviceIdOrderByIdDesc(
                                    device.getId())
                            .size();
        }

        stats.put(
                "devices",
                (long) devices.size());

        stats.put(
                "processes",
                processCount);

        stats.put(
                "windows",
                windowCount);

        stats.put(
                "idle",
                idleCount);

        return stats;
    }
    

    
    public List<ProcessActivity>
    getUserProcesses(String username) {

        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow();

        List<Device> devices =
                deviceRepository
                        .findByUserId(user.getId());

        List<ProcessActivity> result =
                new ArrayList<>();

        for (Device device : devices) {

            result.addAll(
                    processRepository
                            .findByDeviceId(
                                    device.getId()));
        }

        return result;
    }
    
    
    public List<ActiveWindowActivity>
    getUserWindows(String username) {

        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow();

        List<Device> devices =
                deviceRepository
                        .findByUserId(
                                user.getId());

        List<ActiveWindowActivity> result =
                new ArrayList<>();

        for (Device device : devices) {

            result.addAll(
                    windowRepository
                            .findByDeviceIdOrderByIdDesc(
                                    device.getId()));
        }

        return result;
    }
    
    
    public List<IdleActivity>
    getUserIdle(String username) {

        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow();

        List<Device> devices =
                deviceRepository
                        .findByUserId(
                                user.getId());

        List<IdleActivity> result =
                new ArrayList<>();

        for (Device device : devices) {

            result.addAll(
                    idleRepository
                            .findByDeviceIdOrderByIdDesc(
                                    device.getId()));
        }

        return result;
    }
    
    
    public Map<String,Object>
    getProductivity(String username) {

        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow();

        List<Device> devices =
                deviceRepository
                        .findByUserId(
                                user.getId());

        long totalIdle = 0;
        long totalActive = 0;

        List<Map<String,Object>> apps =
                new ArrayList<>();

        for (Device device : devices) {

            List<IdleActivity> idleList =
                    idleRepository
                            .findByDeviceIdOrderByIdDesc(
                                    device.getId());

            totalIdle += idleList.stream()
                    .filter(i ->
                            i.getIdleSeconds() != null)
                    .mapToLong(
                            IdleActivity::getIdleSeconds)
                    .sum();

            List<Object[]> topApps =
                    processRepository
                            .getTopApplications(
                                    device.getId());

            for (Object[] row : topApps) {

                Map<String,Object> app =
                        new HashMap<>();

                app.put(
                        "name",
                        row[0]);

                app.put(
                        "seconds",
                        row[1]);

                apps.add(app);
            }
        }

        totalActive =
                apps.stream()
                        .mapToLong(a ->
                                ((Number)a.get(
                                        "seconds"))
                                        .longValue())
                        .sum();

        Map<String,Object> result =
                new HashMap<>();

        result.put(
                "activeTime",
                totalActive);

        result.put(
                "idleTime",
                totalIdle);

        result.put(
                "apps",
                apps);

        return result;
    }
    
    public UserDashboardDto getDashboard(
            String username) {

        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow();

        Long activeTime =
                processRepository
                        .getTotalActiveTime(
                                user.getId());

        Long idleTime =
                idleRepository
                        .getTotalIdleTime(
                                user.getId());

        Long totalSessions =
                (long) deviceSessionRepository
                        .findByUserIdOrderByIdDesc(
                                user.getId())
                        .size();

        List<AppUsageDto> apps =
                processRepository
                        .topApps(
                                user.getId());

        String topApp =
                apps.isEmpty()
                        ? "-"
                        : apps.get(0)
                              .getName();

        List<DeviceSession> sessions =
        		deviceSessionRepository
                        .findByUserIdOrderByIdDesc(
                                user.getId());

        String lastLogin =
                sessions.isEmpty()
                        ? "-"
                        : String.valueOf(
                                sessions.get(0)
                                        .getStartupTime());

        return UserDashboardDto
                .builder()
                .totalActiveTime(activeTime)
                .totalIdleTime(idleTime)
                .totalSessions(totalSessions)
                .topAppUsed(topApp)
                .lastLoginTime(lastLogin)
                .build();
    }
}