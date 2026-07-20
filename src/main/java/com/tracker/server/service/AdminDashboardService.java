package com.tracker.server.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tracker.server.dto.dashboard.ActiveWindowActivityRow;
import com.tracker.server.dto.dashboard.ActivityTableResponse;
import com.tracker.server.dto.dashboard.AdminDashboardSummaryResponse;
import com.tracker.server.dto.dashboard.AdminDeviceListItemResponse;
import com.tracker.server.dto.dashboard.AdminUserListItemResponse;
import com.tracker.server.dto.dashboard.DeviceOverviewResponse;
import com.tracker.server.dto.dashboard.DeviceSessionRow;
import com.tracker.server.dto.dashboard.IdleActivityRow;
import com.tracker.server.dto.dashboard.ProcessActivityRow;
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

import jakarta.persistence.criteria.Predicate;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final Set<String> CLOSED_SESSION_STATUSES = Set.of("CLOSED", "SHUTDOWN");

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final ProcessActivityRepository processRepository;
    private final ActiveWindowActivityRepository windowRepository;
    private final IdleActivityRepository idleRepository;
    private final DeviceSessionRepository sessionRepository;

    public AdminDashboardService(
            UserRepository userRepository,
            DeviceRepository deviceRepository,
            ProcessActivityRepository processRepository,
            ActiveWindowActivityRepository windowRepository,
            IdleActivityRepository idleRepository,
            DeviceSessionRepository sessionRepository) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.processRepository = processRepository;
        this.windowRepository = windowRepository;
        this.idleRepository = idleRepository;
        this.sessionRepository = sessionRepository;
    }

    public AdminDashboardSummaryResponse getSummary() {
        List<Device> devices = deviceRepository.findAll();
        Map<Long, DeviceSession> latestSessions = latestSessionsByDevice();

        long online = 0;
        long offline = 0;
        long shutdown = 0;

        for (Device device : devices) {
            String displayStatus = classifyDevice(device, latestSessions.get(device.getId()));
            switch (displayStatus) {
                case "ONLINE" -> online++;
                case "SHUTDOWN" -> shutdown++;
                default -> offline++;
            }
        }

        long users = userRepository.findAll().stream()
                .filter(this::isNormalUser)
                .count();

        return new AdminDashboardSummaryResponse(
                devices.size(), online, offline, shutdown, users);
    }

    public List<AdminUserListItemResponse> getUsers(String search) {
        String normalizedSearch = normalize(search);
        List<Device> devices = deviceRepository.findAll();
        Map<Long, DeviceSession> latestSessions = latestSessionsByDevice();
        Map<Long, List<Device>> devicesByUser = devices.stream()
                .filter(device -> device.getUser() != null)
                .collect(Collectors.groupingBy(device -> device.getUser().getId()));

        return userRepository.findAll().stream()
                .filter(this::isNormalUser)
                .filter(user -> normalizedSearch == null
                        || safe(user.getUsername()).toLowerCase(Locale.ROOT).contains(normalizedSearch))
                .sorted(Comparator.comparing(user -> safe(user.getUsername()).toLowerCase(Locale.ROOT)))
                .map(user -> {
                    List<Device> userDevices = devicesByUser.getOrDefault(user.getId(), List.of());
                    long onlineCount = userDevices.stream()
                            .filter(device -> "ONLINE".equals(classifyDevice(
                                    device, latestSessions.get(device.getId()))))
                            .count();
                    return new AdminUserListItemResponse(
                            user.getId(),
                            user.getUsername(),
                            user.getCreatedAt(),
                            userDevices.size(),
                            onlineCount);
                })
                .toList();
    }

    public List<AdminDeviceListItemResponse> getDevices(
            String scope,
            Long userId,
            String search) {

        String normalizedScope = normalizeScope(scope);
        String normalizedSearch = normalize(search);
        Map<Long, DeviceSession> latestSessions = latestSessionsByDevice();

        return deviceRepository.findAll().stream()
                .filter(device -> userId == null
                        || (device.getUser() != null && userId.equals(device.getUser().getId())))
                .map(device -> toDeviceListItem(device, latestSessions.get(device.getId())))
                .filter(device -> scopeMatches(normalizedScope, device.displayStatus()))
                .filter(device -> normalizedSearch == null || deviceMatches(device, normalizedSearch))
                .sorted(Comparator
                        .comparing(AdminDeviceListItemResponse::lastSeen,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(device -> safe(device.machineName()).toLowerCase(Locale.ROOT)))
                .toList();
    }

    public DeviceOverviewResponse getDeviceOverview(Long deviceId, String timezone) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));

        DateRange today = resolveDateRange("TODAY", timezone, null, null);
        long todayProcesses = processRepository.count(processSpec(
                deviceId, null, null, today));
        long runningProcesses = processRepository.countByDeviceIdAndStatus(deviceId, "RUNNING");

        DeviceSession latestSession = sessionRepository.findTopByDeviceIdOrderByStartupTimeDesc(deviceId)
                .orElse(null);
        IdleActivity currentIdle = idleRepository
                .findFirstByDeviceIdAndStatusOrderByIdleStartDesc(deviceId, "RUNNING")
                .orElse(null);
        ActiveWindowActivity currentWindow = windowRepository
                .findFirstByDeviceIdAndStatusOrderByStartTimeDesc(deviceId, "RUNNING")
                .orElse(null);

        return new DeviceOverviewResponse(
                device.getId(),
                device.getUser() == null ? null : device.getUser().getId(),
                device.getUser() == null ? null : device.getUser().getUsername(),
                device.getMachineName(),
                device.getOsName(),
                device.getLastIpAddress(),
                device.getLastSeen(),
                classifyDevice(device, latestSession),
                todayProcesses,
                runningProcesses,
                toSessionSummary(latestSession),
                toIdleSummary(currentIdle),
                toWindowSummary(currentWindow));
    }

    public ActivityTableResponse<?> getActivities(
            Long deviceId,
            String type,
            String datePreset,
            String timezone,
            String from,
            String to,
            String status,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir,
            boolean includeTotalDuration) {

        if (!deviceRepository.existsById(deviceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found");
        }

        String normalizedType = normalizeType(type);
        String normalizedStatus = normalize(status);
        if (normalizedStatus != null) {
            normalizedStatus = normalizedStatus.toUpperCase(Locale.ROOT);
        }
        String normalizedSearch = normalize(search);
        DateRange range = resolveDateRange(datePreset, timezone, from, to);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(10, size));
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return switch (normalizedType) {
            case "processes" -> processTable(deviceId, range, normalizedStatus, normalizedSearch,
                    safePage, safeSize, processSort(sortBy), direction, includeTotalDuration);
            case "windows" -> windowTable(deviceId, range, normalizedStatus, normalizedSearch,
                    safePage, safeSize, windowSort(sortBy), direction, includeTotalDuration);
            case "idle" -> idleTable(deviceId, range, normalizedStatus,
                    safePage, safeSize, idleSort(sortBy), direction, includeTotalDuration);
            case "sessions" -> sessionTable(deviceId, range, normalizedStatus,
                    safePage, safeSize, sessionSort(sortBy), direction, includeTotalDuration);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported activity type");
        };
    }

    private ActivityTableResponse<ProcessActivityRow> processTable(
            Long deviceId,
            DateRange range,
            String status,
            String search,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction,
            boolean includeTotalDuration) {

        Specification<ProcessActivity> spec = processSpec(deviceId, status, search, range);
        Page<ProcessActivity> result = processRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(direction, sortBy)));
        Long totalDuration = includeTotalDuration
                ? processRepository.sumFilteredDuration(
                        deviceId, status, search, range.from(), range.to())
                : null;

        return new ActivityTableResponse<>(
                "processes",
                result.getContent().stream().map(this::toProcessRow).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages(),
                totalDuration);
    }

    private ActivityTableResponse<ActiveWindowActivityRow> windowTable(
            Long deviceId,
            DateRange range,
            String status,
            String search,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction,
            boolean includeTotalDuration) {

        Specification<ActiveWindowActivity> spec = windowSpec(deviceId, status, search, range);
        Page<ActiveWindowActivity> result = windowRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(direction, sortBy)));
        Long totalDuration = includeTotalDuration
                ? windowRepository.sumFilteredDuration(
                        deviceId, status, search, range.from(), range.to())
                : null;

        return new ActivityTableResponse<>(
                "windows",
                result.getContent().stream().map(this::toWindowRow).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages(),
                totalDuration);
    }

    private ActivityTableResponse<IdleActivityRow> idleTable(
            Long deviceId,
            DateRange range,
            String status,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction,
            boolean includeTotalDuration) {

        Specification<IdleActivity> spec = idleSpec(deviceId, status, range);
        Page<IdleActivity> result = idleRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(direction, sortBy)));
        Long totalDuration = includeTotalDuration
                ? idleRepository.sumFilteredDuration(
                        deviceId, status, range.from(), range.to())
                : null;

        return new ActivityTableResponse<>(
                "idle",
                result.getContent().stream().map(this::toIdleRow).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages(),
                totalDuration);
    }

    private ActivityTableResponse<DeviceSessionRow> sessionTable(
            Long deviceId,
            DateRange range,
            String status,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction,
            boolean includeTotalDuration) {

        Specification<DeviceSession> spec = sessionSpec(deviceId, status, range);
        Page<DeviceSession> result = sessionRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(direction, sortBy)));
        Long totalDuration = includeTotalDuration
                ? sessionRepository.sumFilteredDuration(
                        deviceId, status, range.from(), range.to())
                : null;

        return new ActivityTableResponse<>(
                "sessions",
                result.getContent().stream().map(this::toSessionRow).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages(),
                totalDuration);
    }

    private Specification<ProcessActivity> processSpec(
            Long deviceId, String status, String search, DateRange range) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("device").get("id"), deviceId));
            if (status != null) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status));
            }
            if (search != null) {
                predicates.add(cb.like(cb.lower(root.get("processName")), "%" + search + "%"));
            }
            addOverlapPredicates(predicates, cb, root.get("startTime"), root.get("endTime"), range);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<ActiveWindowActivity> windowSpec(
            Long deviceId, String status, String search, DateRange range) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("device").get("id"), deviceId));
            if (status != null) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status));
            }
            if (search != null) {
                predicates.add(cb.like(cb.lower(root.get("windowTitle")), "%" + search + "%"));
            }
            addOverlapPredicates(predicates, cb, root.get("startTime"), root.get("endTime"), range);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<IdleActivity> idleSpec(Long deviceId, String status, DateRange range) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("device").get("id"), deviceId));
            if (status != null) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status));
            }
            addOverlapPredicates(predicates, cb, root.get("idleStart"), root.get("idleEnd"), range);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<DeviceSession> sessionSpec(Long deviceId, String status, DateRange range) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("device").get("id"), deviceId));
            if (status != null) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status));
            }
            addOverlapPredicates(predicates, cb, root.get("startupTime"), root.get("shutdownTime"), range);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addOverlapPredicates(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Path<LocalDateTime> start,
            jakarta.persistence.criteria.Path<LocalDateTime> end,
            DateRange range) {
        if (range.from() != null) {
            predicates.add(cb.or(cb.isNull(end), cb.greaterThanOrEqualTo(end, range.from())));
        }
        if (range.to() != null) {
            predicates.add(cb.lessThanOrEqualTo(start, range.to()));
        }
    }

    private DateRange resolveDateRange(String preset, String timezone, String from, String to) {
        LocalDateTime explicitFrom = parseInstantToUtc(from);
        LocalDateTime explicitTo = parseInstantToUtc(to);
        if (explicitFrom != null || explicitTo != null) {
            if (explicitFrom != null && explicitTo != null && explicitFrom.isAfter(explicitTo)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From time must be before to time");
            }
            return new DateRange(explicitFrom, explicitTo);
        }

        String normalizedPreset = preset == null ? "TODAY" : preset.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalizedPreset)) {
            return new DateRange(null, null);
        }

        ZoneId zone = safeZone(timezone);
        LocalDate date = LocalDate.now(zone);
        if ("YESTERDAY".equals(normalizedPreset)) {
            date = date.minusDays(1);
        } else if ("BEFORE_YESTERDAY".equals(normalizedPreset)) {
            date = date.minusDays(2);
        }

        Instant startInstant = date.atStartOfDay(zone).toInstant();
        Instant endInstant = date.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1);
        return new DateRange(
                LocalDateTime.ofInstant(startInstant, ZoneOffset.UTC),
                LocalDateTime.ofInstant(endInstant, ZoneOffset.UTC));
    }

    private LocalDateTime parseInstantToUtc(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISO date-time", ex);
        }
    }

    private ZoneId safeZone(String timezone) {
        try {
            return timezone == null || timezone.isBlank()
                    ? ZoneOffset.UTC
                    : ZoneId.of(timezone);
        } catch (Exception ignored) {
            return ZoneOffset.UTC;
        }
    }

    private Map<Long, DeviceSession> latestSessionsByDevice() {
        return sessionRepository.findLatestForAllDevices().stream()
                .filter(session -> session.getDevice() != null)
                .collect(Collectors.toMap(
                        session -> session.getDevice().getId(),
                        session -> session,
                        (first, second) -> first.getId() >= second.getId() ? first : second));
    }

    private AdminDeviceListItemResponse toDeviceListItem(Device device, DeviceSession latestSession) {
        return new AdminDeviceListItemResponse(
                device.getId(),
                device.getUser() == null ? null : device.getUser().getId(),
                device.getUser() == null ? null : device.getUser().getUsername(),
                device.getMachineName(),
                device.getOsName(),
                device.getLastIpAddress(),
                device.getLastSeen(),
                classifyDevice(device, latestSession),
                device.isOnline(),
                latestSession == null ? null : latestSession.getStartupTime(),
                latestSession == null ? null : latestSession.getShutdownTime(),
                latestSession == null ? null : effectiveDuration(
                        latestSession.getSessionDurationSeconds(),
                        latestSession.getStartupTime(),
                        latestSession.getShutdownTime()));
    }

    private String classifyDevice(Device device, DeviceSession latestSession) {
        if (device.isOnline()) {
            return "ONLINE";
        }
        String storedStatus = safe(device.getStatus()).toUpperCase(Locale.ROOT);
        if ("UNINSTALLED".equals(storedStatus)) {
            return "UNINSTALLED";
        }
        if ("SHUTDOWN".equals(storedStatus)) {
            return "SHUTDOWN";
        }
        if (latestSession != null
                && latestSession.getShutdownTime() != null
                && CLOSED_SESSION_STATUSES.contains(safe(latestSession.getStatus()).toUpperCase(Locale.ROOT))) {
            return "SHUTDOWN";
        }
        return "OFFLINE";
    }

    private boolean scopeMatches(String scope, String displayStatus) {
        return switch (scope) {
            case "ONLINE" -> "ONLINE".equals(displayStatus);
            case "SHUTDOWN" -> "SHUTDOWN".equals(displayStatus);
            case "OFFLINE" -> "OFFLINE".equals(displayStatus) || "UNINSTALLED".equals(displayStatus);
            default -> true;
        };
    }

    private boolean deviceMatches(AdminDeviceListItemResponse device, String search) {
        return safe(device.machineName()).toLowerCase(Locale.ROOT).contains(search)
                || safe(device.username()).toLowerCase(Locale.ROOT).contains(search)
                || safe(device.osName()).toLowerCase(Locale.ROOT).contains(search)
                || safe(device.lastIpAddress()).toLowerCase(Locale.ROOT).contains(search);
    }

    private String normalizeScope(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "ALL";
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return Set.of("ALL", "ONLINE", "OFFLINE", "SHUTDOWN").contains(normalized)
                ? normalized
                : "ALL";
    }

    private String normalizeType(String value) {
        return value == null ? "processes" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isNormalUser(User user) {
        return "USER".equalsIgnoreCase(user.getRole());
    }

    private String processSort(String sortBy) {
        return allowedSort(sortBy, Set.of(
                "id", "pid", "processName", "status", "startTime", "endTime", "durationSeconds"),
                "startTime");
    }

    private String windowSort(String sortBy) {
        return allowedSort(sortBy, Set.of(
                "id", "windowTitle", "status", "startTime", "endTime", "durationSeconds"),
                "startTime");
    }

    private String idleSort(String sortBy) {
        String field = "durationSeconds".equals(sortBy) ? "idleSeconds" : sortBy;
        return allowedSort(field, Set.of("id", "status", "idleStart", "idleEnd", "idleSeconds"),
                "idleStart");
    }

    private String sessionSort(String sortBy) {
        String field = "durationSeconds".equals(sortBy) ? "sessionDurationSeconds" : sortBy;
        return allowedSort(field, Set.of(
                "id", "status", "startupTime", "shutdownTime", "sessionDurationSeconds"),
                "startupTime");
    }

    private String allowedSort(String requested, Set<String> allowed, String fallback) {
        return requested != null && allowed.contains(requested) ? requested : fallback;
    }

    private ProcessActivityRow toProcessRow(ProcessActivity activity) {
        return new ProcessActivityRow(
                activity.getId(), activity.getPid(), activity.getProcessName(), activity.getStatus(),
                activity.getStartTime(), activity.getEndTime(),
                effectiveDuration(activity.getDurationSeconds(), activity.getStartTime(), activity.getEndTime()));
    }

    private ActiveWindowActivityRow toWindowRow(ActiveWindowActivity activity) {
        return new ActiveWindowActivityRow(
                activity.getId(), activity.getWindowTitle(), activity.getStatus(),
                activity.getStartTime(), activity.getEndTime(),
                effectiveDuration(activity.getDurationSeconds(), activity.getStartTime(), activity.getEndTime()));
    }

    private IdleActivityRow toIdleRow(IdleActivity activity) {
        return new IdleActivityRow(
                activity.getId(), activity.getStatus(), activity.getIdleStart(), activity.getIdleEnd(),
                effectiveDuration(activity.getIdleSeconds(), activity.getIdleStart(), activity.getIdleEnd()));
    }

    private DeviceSessionRow toSessionRow(DeviceSession session) {
        return new DeviceSessionRow(
                session.getId(), session.getStatus(), session.getStartupTime(), session.getShutdownTime(),
                effectiveDuration(session.getSessionDurationSeconds(),
                        session.getStartupTime(), session.getShutdownTime()));
    }

    private DeviceOverviewResponse.SessionSummary toSessionSummary(DeviceSession session) {
        if (session == null) {
            return null;
        }
        return new DeviceOverviewResponse.SessionSummary(
                session.getId(), session.getStatus(), session.getStartupTime(), session.getShutdownTime(),
                effectiveDuration(session.getSessionDurationSeconds(),
                        session.getStartupTime(), session.getShutdownTime()));
    }

    private DeviceOverviewResponse.IdleSummary toIdleSummary(IdleActivity idle) {
        if (idle == null) {
            return null;
        }
        return new DeviceOverviewResponse.IdleSummary(
                idle.getId(), idle.getStatus(), idle.getIdleStart(), idle.getIdleEnd(),
                effectiveDuration(idle.getIdleSeconds(), idle.getIdleStart(), idle.getIdleEnd()));
    }

    private DeviceOverviewResponse.WindowSummary toWindowSummary(ActiveWindowActivity window) {
        if (window == null) {
            return null;
        }
        return new DeviceOverviewResponse.WindowSummary(
                window.getId(), window.getWindowTitle(), window.getStatus(), window.getStartTime(),
                window.getEndTime(), effectiveDuration(
                        window.getDurationSeconds(), window.getStartTime(), window.getEndTime()));
    }

    private long effectiveDuration(Long storedDuration, LocalDateTime start, LocalDateTime end) {
        if (storedDuration != null && storedDuration >= 0) {
            return storedDuration;
        }
        if (start == null) {
            return 0;
        }
        LocalDateTime effectiveEnd = end == null ? LocalDateTime.now(ZoneOffset.UTC) : end;
        return Math.max(0, Duration.between(start, effectiveEnd).getSeconds());
    }


    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record DateRange(LocalDateTime from, LocalDateTime to) {
    }
}
