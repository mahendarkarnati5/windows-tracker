
package com.tracker.server.service;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import com.tracker.server.entity.ActiveWindowActivity;
import com.tracker.server.entity.DeviceSession;
import com.tracker.server.entity.IdleActivity;
import com.tracker.server.entity.ProcessActivity;
import com.tracker.server.repository.ActiveWindowActivityRepository;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.repository.DeviceSessionRepository;
import com.tracker.server.repository.IdleActivityRepository;
import com.tracker.server.repository.ProcessActivityRepository;
import com.tracker.server.util.DateTimeUtil;

@Service
public class ExportService {

    private final ProcessActivityRepository processRepository;
    private final ActiveWindowActivityRepository windowRepository;
    private final IdleActivityRepository idleRepository;
    private final DeviceSessionRepository sessionRepository;
    private final DeviceRepository deviceRepository;

    public ExportService(ProcessActivityRepository processRepository,
                         ActiveWindowActivityRepository windowRepository,
                         IdleActivityRepository idleRepository,
                         DeviceSessionRepository sessionRepository,
                         DeviceRepository deviceRepository) {
        this.processRepository = processRepository;
        this.windowRepository = windowRepository;
        this.idleRepository = idleRepository;
        this.sessionRepository = sessionRepository;
        this.deviceRepository = deviceRepository;
    }

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== EXCEL EXPORT ====================
    public byte[] exportToExcel(List<?> data, String[] headers, String[] fields, String sheetName) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(sheetName);
            
            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            
            // Header row
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Data rows
            int rowNum = 1;
            for (Object item : data) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < fields.length; i++) {
                    Object value = getFieldValue(item, fields[i]);
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(i);
                    String cellValue = "";
                    if (value != null) {
                        if (value instanceof LocalDateTime) {
                            cellValue = ((LocalDateTime) value).format(formatter);
                        } else {
                            cellValue = value.toString();
                        }
                    }
                    cell.setCellValue(cellValue);
                }
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ==================== PDF EXPORT (iText 7) ====================
    public byte[] exportToPdf(List<?> data, String[] headers, String[] fields, String title) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        // Initialize PDF writer and document
        PdfWriter writer = new PdfWriter(out);
        com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(writer);
        Document document = new Document(pdfDoc);
        
        // Add title
        Paragraph titlePara = new Paragraph(title)
                .setFontSize(18)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
        document.add(titlePara);
        
        // Add timestamp
        Paragraph datePara = new Paragraph("Generated: " + DateTimeUtil.now().format(formatter))
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(datePara);
        document.add(new Paragraph(" "));
        
        // Create table
        Table table = new Table(UnitValue.createPercentArray(headers.length));
        table.setWidth(UnitValue.createPercentValue(100));
        
        // Add headers with gray background
        for (String header : headers) {
            Cell cell = new Cell()
                    .add(new Paragraph(header))
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBold();
            table.addCell(cell);
        }
        
        // Add data - properly formatted
        for (Object item : data) {
            for (String field : fields) {
                Object value = getFieldValue(item, field);
                String cellValue = "";
                if (value != null) {
                    if (value instanceof LocalDateTime) {
                        cellValue = ((LocalDateTime) value).format(formatter);
                    } else {
                        cellValue = value.toString();
                    }
                }
                Cell cell = new Cell()
                        .add(new Paragraph(cellValue))
                        .setTextAlignment(TextAlignment.LEFT);
                table.addCell(cell);
            }
        }
        
        document.add(table);
        document.close();
        
        return out.toByteArray();
    }

    // ==================== HELPER METHODS ====================
    private Object getFieldValue(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            // Return the raw value - formatting will be done in the export methods
            return value;
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== SPECIFIC EXPORT METHODS ====================
    
    public byte[] exportProcessesExcel(Long deviceId) throws Exception {
        List<ProcessActivity> data = processRepository.findByDeviceId(deviceId);
        String[] headers = {"ID", "PID", "Process Name", "Start Time", "End Time", "Duration (s)", "Status"};
        String[] fields = {"id", "pid", "processName", "startTime", "endTime", "durationSeconds", "status"};
        return exportToExcel(data, headers, fields, "Processes");
    }

    public byte[] exportProcessesPdf(Long deviceId) throws Exception {
        List<ProcessActivity> data = processRepository.findByDeviceId(deviceId);
        String[] headers = {"ID", "PID", "Process Name", "Start Time", "End Time", "Duration (s)", "Status"};
        String[] fields = {"id", "pid", "processName", "startTime", "endTime", "durationSeconds", "status"};
        return exportToPdf(data, headers, fields, "Process Activities");
    }

    public byte[] exportWindowsExcel(Long deviceId) throws Exception {
        List<ActiveWindowActivity> data = windowRepository.findByDeviceIdOrderByIdDesc(deviceId);
        String[] headers = {"ID", "Window Title", "Start Time", "End Time", "Duration (s)", "Status"};
        String[] fields = {"id", "windowTitle", "startTime", "endTime", "durationSeconds", "status"};
        return exportToExcel(data, headers, fields, "Windows");
    }

    public byte[] exportWindowsPdf(Long deviceId) throws Exception {
        List<ActiveWindowActivity> data = windowRepository.findByDeviceIdOrderByIdDesc(deviceId);
        String[] headers = {"ID", "Window Title", "Start Time", "End Time", "Duration (s)", "Status"};
        String[] fields = {"id", "windowTitle", "startTime", "endTime", "durationSeconds", "status"};
        return exportToPdf(data, headers, fields, "Window Activities");
    }

    public byte[] exportIdleExcel(Long deviceId) throws Exception {
        List<IdleActivity> data = idleRepository.findByDeviceIdOrderByIdDesc(deviceId);
        String[] headers = {"ID", "Idle Start", "Idle End", "Duration (s)", "Status"};
        String[] fields = {"id", "idleStart", "idleEnd", "idleSeconds", "status"};
        return exportToExcel(data, headers, fields, "Idle Activities");
    }

    public byte[] exportIdlePdf(Long deviceId) throws Exception {
        List<IdleActivity> data = idleRepository.findByDeviceIdOrderByIdDesc(deviceId);
        String[] headers = {"ID", "Idle Start", "Idle End", "Duration (s)", "Status"};
        String[] fields = {"id", "idleStart", "idleEnd", "idleSeconds", "status"};
        return exportToPdf(data, headers, fields, "Idle Activities");
    }

    public byte[] exportSessionsExcel(Long deviceId) throws Exception {
        List<DeviceSession> data = sessionRepository.findByDeviceId(deviceId);
        String[] headers = {"ID", "Startup Time", "Shutdown Time", "Duration (s)", "Status"};
        String[] fields = {"id", "startupTime", "shutdownTime", "sessionDurationSeconds", "status"};
        return exportToExcel(data, headers, fields, "Sessions");
    }

    public byte[] exportSessionsPdf(Long deviceId) throws Exception {
        List<DeviceSession> data = sessionRepository.findByDeviceId(deviceId);
        String[] headers = {"ID", "Startup Time", "Shutdown Time", "Duration (s)", "Status"};
        String[] fields = {"id", "startupTime", "shutdownTime", "sessionDurationSeconds", "status"};
        return exportToPdf(data, headers, fields, "Device Sessions");
    }
}