

package com.tracker.server.service;

import com.tracker.server.controller.DeviceController;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

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
import com.tracker.server.specification.ActiveWindowSpecification;
import com.tracker.server.specification.DeviceSessionSpecification;
import com.tracker.server.specification.IdleSpecification;
import com.tracker.server.specification.ProcessActivitySpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

	private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final ProcessActivityRepository processRepository;
    private final ActiveWindowActivityRepository windowRepository;
    private final IdleActivityRepository idleRepository;
    private final DeviceSessionRepository deviceSessionRepository;

	

    // ==================== Get Methods ====================
    
    public List<User> getUsers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole().equals("USER"))
                .toList();
    }
    
    public List<DeviceResponse> getDevices() {
        return deviceRepository.findAll().stream()
                .map(this::toDeviceResponse)
                .collect(Collectors.toList());
    }

    /**
     * Devices whose agent has been uninstalled, so admins can quickly
     * see "so and so uninstalled" without scanning the full device list.
     */
    public List<DeviceResponse> getUninstalledDevices() {
        return deviceRepository.findByStatus("UNINSTALLED").stream()
                .map(this::toDeviceResponse)
                .collect(Collectors.toList());
    }

    private DeviceResponse toDeviceResponse(Device d) {
        return DeviceResponse.builder()
                .id(d.getId())
                .macAddress(d.getMacAddress())
                .machineName(d.getMachineName())
                .osName(d.getOsName())
                .lastIpAddress(d.getLastIpAddress())
                .lastSeen(d.getLastSeen())
                .userId(d.getUser() != null ? d.getUser().getId() : null)
                .username(d.getUser() != null ? d.getUser().getUsername() : null)
                .status(d.getStatus())
                .online(d.isOnline())
                .uninstalledAt(d.getUninstalledAt())
                .build();
    }

    public List<Device> getDevices(Long userId) {
        return deviceRepository.findByUserId(userId);
    }

    public List<ProcessActivity> getProcesses(Long deviceId) {
        return processRepository.findByDeviceId(deviceId);
    }

    public List<ActiveWindowActivity> getWindows(Long deviceId) {
        return windowRepository.findByDeviceIdOrderByIdDesc(deviceId);
    }

    public List<IdleActivity> getIdleActivities(Long deviceId) {
        return idleRepository.findByDeviceIdOrderByIdDesc(deviceId);
    }
    
    public List<DeviceSession> getDeviceSessions(Long deviceId) {
        return deviceSessionRepository.findByDeviceId(deviceId);
    }
    
    // ==================== Export Methods ====================
    
    public ResponseEntity<byte[]> exportProcesses(Long deviceId) throws Exception {
        List<ProcessActivity> processes = processRepository.findByDeviceId(deviceId);
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Processes");
        
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("ID");
        header.createCell(1).setCellValue("Process");
        header.createCell(2).setCellValue("Status");
        header.createCell(3).setCellValue("Start");
        header.createCell(4).setCellValue("End");

        int rowNum = 1;
        for (ProcessActivity p : processes) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(p.getId());
            row.createCell(1).setCellValue(p.getProcessName());
            row.createCell(2).setCellValue(p.getStatus());
            row.createCell(3).setCellValue(String.valueOf(p.getStartTime()));
            row.createCell(4).setCellValue(String.valueOf(p.getEndTime()));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=processes.xlsx")
                .body(out.toByteArray());
    }
    
    // ==================== Filter Methods ====================
    
    public List<ProcessActivity> filterProcesses(ProcessFilterRequest request) {
        return processRepository.findAll(
            ProcessActivitySpecification.filter(request),
            Sort.by(Sort.Direction.DESC, "startTime")
        );
    }
    
    public List<ActiveWindowActivity> filterWindows(ActiveWindowFilterRequest request) {
        return windowRepository.findAll(
            ActiveWindowSpecification.filter(request),
            Sort.by(Sort.Direction.DESC, "startTime")
        );
    }

    public List<IdleActivity> filterIdleActivities(IdleFilterRequest request) {
        return idleRepository.findAll(
            IdleSpecification.filter(request),
            Sort.by(Sort.Direction.DESC, "idleStart")
        );
    }

    public List<DeviceSession> filterDeviceSessions(DeviceSessionFilterRequest request) {
        return deviceSessionRepository.findAll(
            DeviceSessionSpecification.filter(request),
            Sort.by(Sort.Direction.DESC, "startupTime")
        );
    }
}