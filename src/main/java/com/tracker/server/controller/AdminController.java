//package com.tracker.server.controller;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import com.tracker.server.dto.ActiveWindowFilterRequest;
//import com.tracker.server.dto.DeviceSessionFilterRequest;
//import com.tracker.server.dto.IdleFilterRequest;
//import com.tracker.server.dto.ProcessFilterRequest;
//import com.tracker.server.entity.ActiveWindowActivity;
//import com.tracker.server.entity.Device;
//import com.tracker.server.entity.DeviceSession;
//import com.tracker.server.entity.IdleActivity;
//import com.tracker.server.entity.ProcessActivity;
//import com.tracker.server.entity.User;
//import com.tracker.server.repository.ActiveWindowActivityRepository;
//import com.tracker.server.repository.DeviceRepository;
//import com.tracker.server.repository.DeviceSessionRepository;
//import com.tracker.server.repository.IdleActivityRepository;
//import com.tracker.server.repository.ProcessActivityRepository;
//import com.tracker.server.repository.UserRepository;
//import com.tracker.server.service.AdminService;
//import com.tracker.server.service.AuthService;
//
//import lombok.RequiredArgsConstructor;
//
//@RestController
//@RequestMapping("/api/admin")
//@RequiredArgsConstructor
//public class AdminController {
//
//    private final AdminService adminService;
//    private final UserRepository userRepository;
//    private final DeviceRepository deviceRepository;
//    private final ProcessActivityRepository processRepository;
//    private final ActiveWindowActivityRepository windowRepository;
//    private final IdleActivityRepository idleRepository;
//    private final AuthService authService;
//    private final DeviceSessionRepository deviceSessionRepository;
//    
//    
//   
//
//    @GetMapping("/users")
//    public List<User> users() {
//        return adminService.getUsers();
//    }
//
//    @GetMapping("/devices/{userId}")
//    public List<Device> devices(
//            @PathVariable Long userId) {
//
//        return adminService.getDevices(userId);
//    }
//
//    @GetMapping("/processes/{deviceId}")
//    public List<ProcessActivity> processes(
//            @PathVariable Long deviceId) {
//
//        return adminService.getProcesses(deviceId);
//    }
//
//    @GetMapping("/windows/{deviceId}")
//    public List<ActiveWindowActivity> windows(
//            @PathVariable Long deviceId) {
//
//        return adminService.getWindows(deviceId);
//    }
//
//    @GetMapping("/idle/{deviceId}")
//    public List<IdleActivity> idle(
//            @PathVariable Long deviceId) {
//
//        return adminService.getIdleActivities(deviceId);
//    }
//    
//    @GetMapping("/stats")
//    public Map<String, Long> stats() {
//
//        Map<String, Long> stats =
//                new HashMap<>();
//
//        stats.put(
//                "users",
//                userRepository.count());
//
//        stats.put(
//                "devices",
//                deviceRepository.count());
//
//        stats.put(
//                "processes",
//                processRepository.count());
//
//        stats.put(
//                "windows",
//                windowRepository.count());
//
//        stats.put(
//                "idle",
//                idleRepository.count());
//
//        return stats;
//    }
//    @GetMapping("/summary")
//    public Map<String, Object> summary() {
//
//        Map<String, Object> map =
//                new HashMap<>();
//
//        map.put(
//                "totalUsers",
//                userRepository.count());
//
//        map.put(
//                "totalDevices",
//                deviceRepository.count());
//
//        map.put(
//                "totalProcesses",
//                processRepository.count());
//
//        map.put(
//                "totalWindows",
//                windowRepository.count());
//
//        map.put(
//                "totalIdle",
//                idleRepository.count());
//
//        return map;
//    }
//    @GetMapping("/recent-processes")
//    public List<ProcessActivity> recentProcesses() {
//
//        return processRepository
//                .findTop10ByOrderByIdDesc();
//    }
//    @GetMapping("/export/processes/{deviceId}")
//    public ResponseEntity<byte[]> exportProcesses(
//            @PathVariable Long deviceId)
//            throws Exception {
//
//        return adminService.exportProcesses(
//                deviceId);
//    }
//    
//    
//    
//    
//    // Add these endpoints
//    @PostMapping("/processes/filter")
//    public List<ProcessActivity> filterWindows(
//            @RequestBody ProcessFilterRequest request) {
//        System.out.println("UserId : " + request.getUserId());
//        System.out.println("DeviceId : " + request.getDeviceId());
//        System.out.println("Status : " + request.getStatus());
//        return adminService.filterProcesses(request);
//    }
//    
//   
//    // Add these endpoints
//    @PostMapping("/windows/filter")
//    public List<ActiveWindowActivity> filterWindows(
//            @RequestBody ActiveWindowFilterRequest request) {
//        System.out.println("UserId : " + request.getUserId());
//        System.out.println("DeviceId : " + request.getDeviceId());
//        System.out.println("WindowTitle : " + request.getWindowTitle());
//        System.out.println("Status : " + request.getStatus());
//        return adminService.filterWindows(request);
//    }
//
//    @PostMapping("/idle/filter")
//    public List<IdleActivity> filterIdle(
//            @RequestBody IdleFilterRequest request) {
//        System.out.println("UserId : " + request.getUserId());
//        System.out.println("DeviceId : " + request.getDeviceId());
//        System.out.println("Status : " + request.getStatus());
//        return adminService.filterIdleActivities(request);
//    }
//
//    @PostMapping("/sessions/filter")
//    public List<DeviceSession> filterSessions(
//            @RequestBody DeviceSessionFilterRequest request) {
//        System.out.println("UserId : " + request.getUserId());
//        System.out.println("DeviceId : " + request.getDeviceId());
//        System.out.println("Status : " + request.getStatus());
//        return adminService.filterDeviceSessions(request);
//    }
//}


package com.tracker.server.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tracker.server.dto.ActiveWindowFilterRequest;
import com.tracker.server.dto.DeviceResponse;
import com.tracker.server.dto.DeviceSessionFilterRequest;
import com.tracker.server.dto.IdleFilterRequest;
import com.tracker.server.dto.ProcessFilterRequest;
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
import com.tracker.server.service.AdminService;
import com.tracker.server.service.AuthService;
import com.tracker.server.service.ExportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final ProcessActivityRepository processRepository;
    private final ActiveWindowActivityRepository windowRepository;
    private final IdleActivityRepository idleRepository;
    private final DeviceSessionRepository deviceSessionRepository;
    private final AuthService authService;
    private final ExportService exportService;
    
    // ==================== GET Endpoints ====================
    
    @GetMapping("/users")
    public List<User> users() {
        log.info("Fetching all users");
        return adminService.getUsers();
    }
    @GetMapping("/devices")
    public List<DeviceResponse> devices(){
    	return adminService.getDevices();
    }

    /**
     * Highlighted view: only devices whose agent was uninstalled.
     * e.g. GET /api/admin/devices/uninstalled -> [{ username: "john", machineName: "JOHN-PC", uninstalledAt: ... }]
     */
    @GetMapping("/devices/uninstalled")
    public List<DeviceResponse> uninstalledDevices(){
    	return adminService.getUninstalledDevices();
    }

    @GetMapping("/devices/{userId}")
    public List<Device> devices(@PathVariable Long userId) {
        log.info("Fetching devices for userId: {}", userId);
        return adminService.getDevices(userId);
    }

    @GetMapping("/processes/{deviceId}")
    public List<ProcessActivity> processes(@PathVariable Long deviceId) {
        log.info("Fetching processes for deviceId: {}", deviceId);
        return adminService.getProcesses(deviceId);
    }

    @GetMapping("/windows/{deviceId}")
    public List<ActiveWindowActivity> windows(@PathVariable Long deviceId) {
        log.info("Fetching windows for deviceId: {}", deviceId);
        return adminService.getWindows(deviceId);
    }

    @GetMapping("/idle/{deviceId}")
    public List<IdleActivity> idle(@PathVariable Long deviceId) {
        log.info("Fetching idle activities for deviceId: {}", deviceId);
        return adminService.getIdleActivities(deviceId);
    }
    
    @GetMapping("/sessions/{deviceId}")
    public List<DeviceSession> sessions(@PathVariable Long deviceId) {
        log.info("Fetching device sessions for deviceId: {}", deviceId);
        return adminService.getDeviceSessions(deviceId);
    }
    
    // ==================== Stats and Summary ====================
    
    @GetMapping("/stats")
    public Map<String, Long> stats() {
        log.info("Fetching statistics");
        Map<String, Long> stats = new HashMap<>();
        stats.put("users", userRepository.count());
        stats.put("devices", deviceRepository.count());
        stats.put("processes", processRepository.count());
        stats.put("windows", windowRepository.count());
        stats.put("idle", idleRepository.count());
        stats.put("sessions", deviceSessionRepository.count());
        log.info("Stats fetched: {}", stats);
        return stats;
    }
    
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        log.info("Fetching summary");
        Map<String, Object> map = new HashMap<>();
        map.put("totalUsers", userRepository.count());
        map.put("totalDevices", deviceRepository.count());
        map.put("totalProcesses", processRepository.count());
        map.put("totalWindows", windowRepository.count());
        map.put("totalIdle", idleRepository.count());
        map.put("totalSessions", deviceSessionRepository.count());
        log.info("Summary fetched: {}", map);
        return map;
    }
    
    @GetMapping("/recent-processes")
    public List<ProcessActivity> recentProcesses() {
        log.info("Fetching recent processes");
        return processRepository.findTop10ByOrderByIdDesc();
    }
    
    // ==================== Export Endpoints ====================
    
    @GetMapping("/export/processes/{deviceId}")
    public ResponseEntity<byte[]> exportProcesses(@PathVariable Long deviceId) throws Exception {
        log.info("Exporting processes for deviceId: {}", deviceId);
        return adminService.exportProcesses(deviceId);
    }
    
    // ==================== Filter Endpoints (POST) ====================
    
    /**
     * Filter Process Activities with multiple criteria
     * Example Request:
     * {
     *     "userId": 1,
     *     "deviceId": 5,
     *     "processName": "chrome",
     *     "status": "ACTIVE",
     *     "pid": 1234,
     *     "startTimeFrom": "2026-01-01T00:00:00",
     *     "startTimeTo": "2026-06-30T23:59:59",
     *     "endTimeFrom": "2026-01-01T00:00:00",
     *     "endTimeTo": "2026-06-30T23:59:59",
     *     "fromDate": "2026-01-01T00:00:00",
     *     "endDate": "2026-06-30T23:59:59",
     *     "durationMin": 60,
     *     "durationMax": 3600
     * }
     */
    @PostMapping("/processes/filter")
    public List<ProcessActivity> filterProcesses(@RequestBody ProcessFilterRequest request) {
        log.info("=== Process Filter Request ===");
        log.info("UserId: {}", request.getUserId());
        log.info("DeviceId: {}", request.getDeviceId());
        log.info("ProcessName: {}", request.getProcessName());
        log.info("Status: {}", request.getStatus());
        log.info("PID: {}", request.getPid());
        log.info("StartTimeFrom: {}", request.getStartTimeFrom());
        log.info("StartTimeTo: {}", request.getStartTimeTo());
        log.info("EndTimeFrom: {}", request.getEndTimeFrom());
        log.info("EndTimeTo: {}", request.getEndTimeTo());
        log.info("FromDate: {}", request.getFromDate());
        log.info("EndDate: {}", request.getEndDate());
        log.info("DurationMin: {}", request.getDurationMin());
        log.info("DurationMax: {}", request.getDurationMax());
        
        List<ProcessActivity> result = adminService.filterProcesses(request);
        log.info("Filtered processes count: {}", result.size());
        return result;
    }
    
    /**
     * Filter Active Window Activities with multiple criteria
     * Example Request:
     * {
     *     "userId": 1,
     *     "deviceId": 5,
     *     "windowTitle": "Google Chrome",
     *     "status": "ACTIVE",
     *     "startTimeFrom": "2026-01-01T00:00:00",
     *     "startTimeTo": "2026-06-30T23:59:59",
     *     "endTimeFrom": "2026-01-01T00:00:00",
     *     "endTimeTo": "2026-06-30T23:59:59",
     *     "fromDate": "2026-01-01T00:00:00",
     *     "endDate": "2026-06-30T23:59:59",
     *     "durationMin": 60,
     *     "durationMax": 3600
     * }
     */
    @PostMapping("/windows/filter")
    public List<ActiveWindowActivity> filterWindows(@RequestBody ActiveWindowFilterRequest request) {
        log.info("=== Window Filter Request ===");
        log.info("UserId: {}", request.getUserId());
        log.info("DeviceId: {}", request.getDeviceId());
        log.info("WindowTitle: {}", request.getWindowTitle());
        log.info("Status: {}", request.getStatus());
        log.info("StartTimeFrom: {}", request.getStartTimeFrom());
        log.info("StartTimeTo: {}", request.getStartTimeTo());
        log.info("EndTimeFrom: {}", request.getEndTimeFrom());
        log.info("EndTimeTo: {}", request.getEndTimeTo());
        log.info("FromDate: {}", request.getFromDate());
        log.info("EndDate: {}", request.getEndDate());
        log.info("DurationMin: {}", request.getDurationMin());
        log.info("DurationMax: {}", request.getDurationMax());
        
        List<ActiveWindowActivity> result = adminService.filterWindows(request);
        log.info("Filtered windows count: {}", result.size());
        return result;
    }
    
    /**
     * Filter Idle Activities with multiple criteria
     * Example Request:
     * {
     *     "userId": 1,
     *     "deviceId": 5,
     *     "status": "COMPLETED",
     *     "idleStartFrom": "2026-01-01T00:00:00",
     *     "idleStartTo": "2026-06-30T23:59:59",
     *     "idleEndFrom": "2026-01-01T00:00:00",
     *     "idleEndTo": "2026-06-30T23:59:59",
     *     "fromDate": "2026-01-01T00:00:00",
     *     "endDate": "2026-06-30T23:59:59",
     *     "idleSecondsMin": 300,
     *     "idleSecondsMax": 3600
     * }
     */
    @PostMapping("/idle/filter")
    public List<IdleActivity> filterIdle(@RequestBody IdleFilterRequest request) {
        log.info("=== Idle Filter Request ===");
        log.info("UserId: {}", request.getUserId());
        log.info("DeviceId: {}", request.getDeviceId());
        log.info("Status: {}", request.getStatus());
        log.info("IdleStartFrom: {}", request.getIdleStartFrom());
        log.info("IdleStartTo: {}", request.getIdleStartTo());
        log.info("IdleEndFrom: {}", request.getIdleEndFrom());
        log.info("IdleEndTo: {}", request.getIdleEndTo());
        log.info("FromDate: {}", request.getFromDate());
        log.info("EndDate: {}", request.getEndDate());
        log.info("IdleSecondsMin: {}", request.getIdleSecondsMin());
        log.info("IdleSecondsMax: {}", request.getIdleSecondsMax());
        
        List<IdleActivity> result = adminService.filterIdleActivities(request);
        log.info("Filtered idle activities count: {}", result.size());
        return result;
    }
    
    /**
     * Filter Device Sessions with multiple criteria
     * Example Request:
     * {
     *     "userId": 1,
     *     "deviceId": 5,
     *     "status": "COMPLETED",
     *     "startupTimeFrom": "2026-01-01T00:00:00",
     *     "startupTimeTo": "2026-06-30T23:59:59",
     *     "shutdownTimeFrom": "2026-01-01T00:00:00",
     *     "shutdownTimeTo": "2026-06-30T23:59:59",
     *     "fromDate": "2026-01-01T00:00:00",
     *     "endDate": "2026-06-30T23:59:59",
     *     "sessionDurationMin": 3600,
     *     "sessionDurationMax": 86400
     * }
     */
    @PostMapping("/sessions/filter")
    public List<DeviceSession> filterSessions(@RequestBody DeviceSessionFilterRequest request) {
        log.info("=== Session Filter Request ===");
        log.info("UserId: {}", request.getUserId());
        log.info("DeviceId: {}", request.getDeviceId());
        log.info("Status: {}", request.getStatus());
        log.info("StartupTimeFrom: {}", request.getStartupTimeFrom());
        log.info("StartupTimeTo: {}", request.getStartupTimeTo());
        log.info("ShutdownTimeFrom: {}", request.getShutdownTimeFrom());
        log.info("ShutdownTimeTo: {}", request.getShutdownTimeTo());
        log.info("FromDate: {}", request.getFromDate());
        log.info("EndDate: {}", request.getEndDate());
        log.info("SessionDurationMin: {}", request.getSessionDurationMin());
        log.info("SessionDurationMax: {}", request.getSessionDurationMax());
        
        List<DeviceSession> result = adminService.filterDeviceSessions(request);
        log.info("Filtered sessions count: {}", result.size());
        return result;
    }
    
    
    
    
 // Add these endpoints to AdminController.java

 // ==================== PROCESS EXPORTS ====================
 @GetMapping("/export/processes/excel/{deviceId}")
 public ResponseEntity<byte[]> exportProcessesExcel(@PathVariable Long deviceId) {
     try {
         byte[] data = exportService.exportProcessesExcel(deviceId);
         return ResponseEntity.ok()
                 .header(HttpHeaders.CONTENT_DISPOSITION, 
                         "attachment; filename=processes_" + deviceId + ".xlsx")
                 .body(data);
     } catch (Exception e) {
         return ResponseEntity.internalServerError().build();
     }
 }

 @GetMapping("/export/processes/pdf/{deviceId}")
 public ResponseEntity<byte[]> exportProcessesPdf(@PathVariable Long deviceId) {
     try {
         byte[] data = exportService.exportProcessesPdf(deviceId);
         return ResponseEntity.ok()
                 .header(HttpHeaders.CONTENT_DISPOSITION, 
                         "attachment; filename=processes_" + deviceId + ".pdf")
                 .body(data);
     } catch (Exception e) {
         return ResponseEntity.internalServerError().build();
     }
 }

 // ==================== WINDOW EXPORTS ====================
 @GetMapping("/export/windows/excel/{deviceId}")
 public ResponseEntity<byte[]> exportWindowsExcel(@PathVariable Long deviceId) {
     try {
         byte[] data = exportService.exportWindowsExcel(deviceId);
         return ResponseEntity.ok()
                 .header(HttpHeaders.CONTENT_DISPOSITION, 
                         "attachment; filename=windows_" + deviceId + ".xlsx")
                 .body(data);
     } catch (Exception e) {
         return ResponseEntity.internalServerError().build();
     }
 }

 @GetMapping("/export/windows/pdf/{deviceId}")
 public ResponseEntity<byte[]> exportWindowsPdf(@PathVariable Long deviceId) {
     try {
         byte[] data = exportService.exportWindowsPdf(deviceId);
         return ResponseEntity.ok()
                 .header(HttpHeaders.CONTENT_DISPOSITION, 
                         "attachment; filename=windows_" + deviceId + ".pdf")
                 .body(data);
     } catch (Exception e) {
         return ResponseEntity.internalServerError().build();
     }
 }

 // ==================== IDLE EXPORTS ====================
 @GetMapping("/export/idle/excel/{deviceId}")
 public ResponseEntity<byte[]> exportIdleExcel(@PathVariable Long deviceId) {
     try {
         byte[] data = exportService.exportIdleExcel(deviceId);
         return ResponseEntity.ok()
                 .header(HttpHeaders.CONTENT_DISPOSITION, 
                         "attachment; filename=idle_" + deviceId + ".xlsx")
                 .body(data);
     } catch (Exception e) {
         return ResponseEntity.internalServerError().build();
     }
 }

 @GetMapping("/export/idle/pdf/{deviceId}")
 public ResponseEntity<byte[]> exportIdlePdf(@PathVariable Long deviceId) {
     try {
         byte[] data = exportService.exportIdlePdf(deviceId);
         return ResponseEntity.ok()
                 .header(HttpHeaders.CONTENT_DISPOSITION, 
                         "attachment; filename=idle_" + deviceId + ".pdf")
                 .body(data);
     } catch (Exception e) {
         return ResponseEntity.internalServerError().build();
     }
 }

 // ==================== SESSION EXPORTS ====================
 @GetMapping("/export/sessions/excel/{deviceId}")
 public ResponseEntity<byte[]> exportSessionsExcel(@PathVariable Long deviceId) {
     try {
         byte[] data = exportService.exportSessionsExcel(deviceId);
         return ResponseEntity.ok()
                 .header(HttpHeaders.CONTENT_DISPOSITION, 
                         "attachment; filename=sessions_" + deviceId + ".xlsx")
                 .body(data);
     } catch (Exception e) {
         return ResponseEntity.internalServerError().build();
     }
 }

 @GetMapping("/export/sessions/pdf/{deviceId}")
 public ResponseEntity<byte[]> exportSessionsPdf(@PathVariable Long deviceId) {
     try {
         byte[] data = exportService.exportSessionsPdf(deviceId);
         return ResponseEntity.ok()
                 .header(HttpHeaders.CONTENT_DISPOSITION, 
                         "attachment; filename=sessions_" + deviceId + ".pdf")
                 .body(data);
     } catch (Exception e) {
         return ResponseEntity.internalServerError().build();
     }
 }
}