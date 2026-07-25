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

import org.springframework.beans.factory.annotation.Value;
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


    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final ProcessActivityRepository processRepository;
    private final ActiveWindowActivityRepository windowRepository;
    private final IdleActivityRepository idleRepository;
    private final DeviceSessionRepository sessionRepository;

    @Value("${tracker.agent.offline-after-seconds:45}")
    private long offlineAfterSeconds;

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
        long total = deviceRepository.count();
        long online = deviceRepository.countByOnlineTrueAndLastSeenGreaterThanEqual(onlineCutoff());
        long shutdown = deviceRepository.countByOnlineFalseAndStatusIgnoreCase("SHUTDOWN");
        long offline = Math.max(0L, total - online - shutdown);
        long users = userRepository.countByRoleIgnoreCase("USER");

        return new AdminDashboardSummaryResponse(
                total, online, offline, shutdown, users);
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
        long runningProcesses = processRepository.count(processSpec(
                deviceId, "RUNNING", null, new DateRange(null, null)));

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
                toSessionSummary(latestSession, device),
                toIdleSummary(currentIdle, device),
                toWindowSummary(currentWindow, device));
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

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Device not found"));

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

        String queryStatus = queryStatus(device, normalizedStatus);
        if ("__NONE__".equals(queryStatus)) {
            return emptyTable(normalizedType, safePage, safeSize);
        }

        return switch (normalizedType) {
            case "processes" -> processTable(device, range, queryStatus, normalizedSearch,
                    safePage, safeSize, processSort(sortBy), direction, includeTotalDuration);
            case "windows" -> windowTable(device, range, queryStatus, normalizedSearch,
                    safePage, safeSize, windowSort(sortBy), direction, includeTotalDuration);
            case "idle" -> idleTable(device, range, queryStatus,
                    safePage, safeSize, idleSort(sortBy), direction, includeTotalDuration);
            case "sessions" -> sessionTable(device, range, queryStatus,
                    safePage, safeSize, sessionSort(sortBy), direction, includeTotalDuration);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported activity type");
        };
    }

    private ActivityTableResponse<ProcessActivityRow> processTable(
            Device device,
            DateRange range,
            String status,
            String search,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction,
            boolean includeTotalDuration) {

        Long deviceId = device.getId();
        Specification<ProcessActivity> spec = processSpec(deviceId, status, search, range);
        Page<ProcessActivity> result = processRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(direction, sortBy)));
        Long totalDuration = includeTotalDuration
                ? processRepository.sumFilteredDuration(
                        deviceId, status, search, range.from(), range.to())
                : null;

        return new ActivityTableResponse<>(
                "processes",
                result.getContent().stream().map(row -> toProcessRow(row, device)).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages(),
                totalDuration);
    }

    private ActivityTableResponse<ActiveWindowActivityRow> windowTable(
            Device device,
            DateRange range,
            String status,
            String search,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction,
            boolean includeTotalDuration) {

        Long deviceId = device.getId();
        Specification<ActiveWindowActivity> spec = windowSpec(deviceId, status, search, range);
        Page<ActiveWindowActivity> result = windowRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(direction, sortBy)));
        Long totalDuration = includeTotalDuration
                ? windowRepository.sumFilteredDuration(
                        deviceId, status, search, range.from(), range.to())
                : null;

        return new ActivityTableResponse<>(
                "windows",
                result.getContent().stream().map(row -> toWindowRow(row, device)).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages(),
                totalDuration);
    }

    private ActivityTableResponse<IdleActivityRow> idleTable(
            Device device,
            DateRange range,
            String status,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction,
            boolean includeTotalDuration) {

        Long deviceId = device.getId();
        Specification<IdleActivity> spec = idleSpec(deviceId, status, range);
        Page<IdleActivity> result = idleRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(direction, sortBy)));
        Long totalDuration = includeTotalDuration
                ? idleRepository.sumFilteredDuration(
                        deviceId, status, range.from(), range.to())
                : null;

        return new ActivityTableResponse<>(
                "idle",
                result.getContent().stream().map(row -> toIdleRow(row, device)).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages(),
                totalDuration);
    }

    private ActivityTableResponse<DeviceSessionRow> sessionTable(
            Device device,
            DateRange range,
            String status,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction,
            boolean includeTotalDuration) {

        Long deviceId = device.getId();
        Specification<DeviceSession> spec = sessionSpec(deviceId, status, range);
        Page<DeviceSession> result = sessionRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(direction, sortBy)));
        Long totalDuration = includeTotalDuration
                ? sessionRepository.sumFilteredDuration(
                        deviceId, status, range.from(), range.to())
                : null;

        return new ActivityTableResponse<>(
                "sessions",
                result.getContent().stream().map(row -> toSessionRow(row, device)).toList(),
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
            predicates.add(cb.equal(root.get("device").<Long>get("id"), deviceId));
            addStatusPredicate(
                    predicates, cb, root.<String>get("status"),
                    root.<LocalDateTime>get("endTime"), status);
            if (search != null) {
                predicates.add(cb.like(cb.lower(root.<String>get("processName")), "%" + search + "%"));
            }
            addOverlapPredicates(predicates, cb, root.<LocalDateTime>get("startTime"), root.<LocalDateTime>get("endTime"), range);

            var latest = query.subquery(Long.class);
            var other = latest.from(ProcessActivity.class);
            latest.select(cb.max(other.<Long>get("id")));
            latest.where(
                    cb.equal(other.get("device").<Long>get("id"), root.get("device").<Long>get("id")),
                    sameNullable(cb, other.<Long>get("pid"), root.<Long>get("pid")),
                    cb.equal(
                            cb.lower(cb.coalesce(other.<String>get("processName"), "")),
                            cb.lower(cb.coalesce(root.<String>get("processName"), ""))),
                    sameNullable(cb, other.<LocalDateTime>get("startTime"), root.<LocalDateTime>get("startTime")));
            predicates.add(cb.equal(root.<Long>get("id"), latest));

            // Historical databases may already contain more than one open row for the
            // same PID. The dashboard must still expose only the newest live process.
            var latestOpen = query.subquery(Long.class);
            var open = latestOpen.from(ProcessActivity.class);
            latestOpen.select(cb.max(open.<Long>get("id")));
            latestOpen.where(
                    cb.equal(open.get("device").<Long>get("id"), deviceId),
                    sameNullable(cb, open.<Long>get("pid"), root.<Long>get("pid")),
                    effectiveOpenPredicate(
                            cb,
                            open.<String>get("status"),
                            open.<LocalDateTime>get("endTime")));
            predicates.add(cb.or(
                    cb.not(effectiveOpenPredicate(
                            cb,
                            root.<String>get("status"),
                            root.<LocalDateTime>get("endTime"))),
                    cb.equal(root.<Long>get("id"), latestOpen)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<ActiveWindowActivity> windowSpec(
            Long deviceId, String status, String search, DateRange range) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("device").<Long>get("id"), deviceId));
            addStatusPredicate(
                    predicates, cb, root.<String>get("status"),
                    root.<LocalDateTime>get("endTime"), status);
            if (search != null) {
                predicates.add(cb.like(cb.lower(root.<String>get("windowTitle")), "%" + search + "%"));
            }
            addOverlapPredicates(predicates, cb, root.<LocalDateTime>get("startTime"), root.<LocalDateTime>get("endTime"), range);

            var latestNatural = query.subquery(Long.class);
            var other = latestNatural.from(ActiveWindowActivity.class);
            latestNatural.select(cb.max(other.<Long>get("id")));
            latestNatural.where(
                    cb.equal(other.get("device").<Long>get("id"), root.get("device").<Long>get("id")),
                    sameNullable(cb, other.<Long>get("pid"), root.<Long>get("pid")),
                    cb.equal(
                            cb.lower(cb.coalesce(other.<String>get("processName"), "")),
                            cb.lower(cb.coalesce(root.<String>get("processName"), ""))),
                    cb.equal(
                            cb.coalesce(other.<String>get("windowTitle"), ""),
                            cb.coalesce(root.<String>get("windowTitle"), "")),
                    sameNullable(cb, other.<LocalDateTime>get("startTime"), root.<LocalDateTime>get("startTime")));
            predicates.add(cb.equal(root.<Long>get("id"), latestNatural));

            var latestOpen = query.subquery(Long.class);
            var open = latestOpen.from(ActiveWindowActivity.class);
            latestOpen.select(cb.max(open.<Long>get("id")));
            latestOpen.where(
                    cb.equal(open.get("device").<Long>get("id"), deviceId),
                    effectiveOpenPredicate(
                            cb,
                            open.<String>get("status"),
                            open.<LocalDateTime>get("endTime")));
            predicates.add(cb.or(
                    cb.not(effectiveOpenPredicate(
                            cb,
                            root.<String>get("status"),
                            root.<LocalDateTime>get("endTime"))),
                    cb.equal(root.<Long>get("id"), latestOpen)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<IdleActivity> idleSpec(Long deviceId, String status, DateRange range) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("device").<Long>get("id"), deviceId));
            addStatusPredicate(
                    predicates, cb, root.<String>get("status"),
                    root.<LocalDateTime>get("idleEnd"), status);
            addOverlapPredicates(predicates, cb, root.<LocalDateTime>get("idleStart"), root.<LocalDateTime>get("idleEnd"), range);

            var latestNatural = query.subquery(Long.class);
            var other = latestNatural.from(IdleActivity.class);
            latestNatural.select(cb.max(other.<Long>get("id")));
            latestNatural.where(
                    cb.equal(other.get("device").<Long>get("id"), root.get("device").<Long>get("id")),
                    sameNullable(cb, other.<LocalDateTime>get("idleStart"), root.<LocalDateTime>get("idleStart")));
            predicates.add(cb.equal(root.<Long>get("id"), latestNatural));

            var latestOpen = query.subquery(Long.class);
            var open = latestOpen.from(IdleActivity.class);
            latestOpen.select(cb.max(open.<Long>get("id")));
            latestOpen.where(
                    cb.equal(open.get("device").<Long>get("id"), deviceId),
                    effectiveOpenPredicate(
                            cb,
                            open.<String>get("status"),
                            open.<LocalDateTime>get("idleEnd")));
            predicates.add(cb.or(
                    cb.not(effectiveOpenPredicate(
                            cb,
                            root.<String>get("status"),
                            root.<LocalDateTime>get("idleEnd"))),
                    cb.equal(root.<Long>get("id"), latestOpen)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<DeviceSession> sessionSpec(Long deviceId, String status, DateRange range) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("device").<Long>get("id"), deviceId));
            addStatusPredicate(
                    predicates, cb, root.<String>get("status"),
                    root.<LocalDateTime>get("shutdownTime"), status);
            addOverlapPredicates(predicates, cb, root.<LocalDateTime>get("startupTime"), root.<LocalDateTime>get("shutdownTime"), range);

            var latestNatural = query.subquery(Long.class);
            var other = latestNatural.from(DeviceSession.class);
            latestNatural.select(cb.max(other.<Long>get("id")));
            latestNatural.where(
                    cb.equal(other.get("device").<Long>get("id"), root.get("device").<Long>get("id")),
                    sameNullable(cb, other.<LocalDateTime>get("startupTime"), root.<LocalDateTime>get("startupTime")));
            predicates.add(cb.equal(root.<Long>get("id"), latestNatural));

            var latestOpen = query.subquery(Long.class);
            var open = latestOpen.from(DeviceSession.class);
            latestOpen.select(cb.max(open.<Long>get("id")));
            latestOpen.where(
                    cb.equal(open.get("device").<Long>get("id"), deviceId),
                    effectiveOpenPredicate(
                            cb,
                            open.<String>get("status"),
                            open.<LocalDateTime>get("shutdownTime")));
            predicates.add(cb.or(
                    cb.not(effectiveOpenPredicate(
                            cb,
                            root.<String>get("status"),
                            root.<LocalDateTime>get("shutdownTime"))),
                    cb.equal(root.<Long>get("id"), latestOpen)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addStatusPredicate(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Path<String> statusPath,
            jakarta.persistence.criteria.Path<LocalDateTime> endPath,
            String requestedStatus) {
        if (requestedStatus == null) {
            return;
        }
        var upper = cb.upper(statusPath);
        if ("CLOSED".equals(requestedStatus)) {
            predicates.add(cb.or(
                    cb.equal(upper, "CLOSED"),
                    cb.equal(upper, "INTERRUPTED"),
                    cb.and(cb.equal(upper, "OFFLINE"), cb.isNotNull(endPath))));
            return;
        }
        if ("RUNNING".equals(requestedStatus)) {
            predicates.add(cb.or(
                    cb.equal(upper, "RUNNING"),
                    cb.and(cb.equal(upper, "OFFLINE"), cb.isNull(endPath))));
            return;
        }
        predicates.add(cb.equal(upper, requestedStatus));
    }

    private Predicate effectiveOpenPredicate(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Path<String> statusPath,
            jakarta.persistence.criteria.Path<LocalDateTime> endPath) {
        var upper = cb.upper(cb.coalesce(statusPath, ""));
        return cb.or(
                cb.equal(upper, "RUNNING"),
                cb.and(cb.equal(upper, "OFFLINE"), cb.isNull(endPath)));
    }

    private Predicate sameNullable(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Expression<?> first,
            jakarta.persistence.criteria.Expression<?> second) {
        return cb.or(
                cb.equal(first, second),
                cb.and(cb.isNull(first), cb.isNull(second)));
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
                isDeviceOnline(device),
                latestSession == null ? null : latestSession.getStartupTime(),
                latestSession == null ? null : latestSession.getShutdownTime(),
                latestSession == null ? null : effectiveDuration(
                        latestSession.getStatus(),
                        latestSession.getSessionDurationSeconds(),
                        latestSession.getStartupTime(),
                        latestSession.getShutdownTime(),
                        device));
    }

    private String classifyDevice(Device device, DeviceSession latestSession) {
        if (isDeviceOnline(device)) {
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
                && "SHUTDOWN".equals(safe(latestSession.getStatus()).toUpperCase(Locale.ROOT))) {
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

    private ProcessActivityRow toProcessRow(ProcessActivity activity, Device device) {
        LocalDateTime displayEnd = effectiveEndTime(
                activity.getStatus(), activity.getEndTime(), device);
        return new ProcessActivityRow(
                activity.getId(),
                activity.getPid(),
                activity.getProcessName(),
                effectiveActivityStatus(activity.getStatus(), activity.getEndTime(), device),
                activity.getStartTime(),
                displayEnd,
                effectiveDuration(
                        activity.getStatus(),
                        activity.getDurationSeconds(),
                        activity.getStartTime(),
                        activity.getEndTime(),
                        device));
    }

    private ActiveWindowActivityRow toWindowRow(
            ActiveWindowActivity activity, Device device) {
        LocalDateTime displayEnd = effectiveEndTime(
                activity.getStatus(), activity.getEndTime(), device);
        return new ActiveWindowActivityRow(
                activity.getId(),
                activity.getWindowTitle(),
                effectiveActivityStatus(activity.getStatus(), activity.getEndTime(), device),
                activity.getStartTime(),
                displayEnd,
                effectiveDuration(
                        activity.getStatus(),
                        activity.getDurationSeconds(),
                        activity.getStartTime(),
                        activity.getEndTime(),
                        device));
    }

    private IdleActivityRow toIdleRow(IdleActivity activity, Device device) {
        LocalDateTime displayEnd = effectiveEndTime(
                activity.getStatus(), activity.getIdleEnd(), device);
        return new IdleActivityRow(
                activity.getId(),
                effectiveActivityStatus(activity.getStatus(), activity.getIdleEnd(), device),
                activity.getIdleStart(),
                displayEnd,
                effectiveDuration(
                        activity.getStatus(),
                        activity.getIdleSeconds(),
                        activity.getIdleStart(),
                        activity.getIdleEnd(),
                        device));
    }

    private DeviceSessionRow toSessionRow(DeviceSession session, Device device) {
        LocalDateTime displayEnd = effectiveEndTime(
                session.getStatus(), session.getShutdownTime(), device);
        return new DeviceSessionRow(
                session.getId(),
                effectiveActivityStatus(session.getStatus(), session.getShutdownTime(), device),
                session.getStartupTime(),
                displayEnd,
                effectiveDuration(
                        session.getStatus(),
                        session.getSessionDurationSeconds(),
                        session.getStartupTime(),
                        session.getShutdownTime(),
                        device));
    }

    private DeviceOverviewResponse.SessionSummary toSessionSummary(
            DeviceSession session, Device device) {
        if (session == null) {
            return null;
        }
        return new DeviceOverviewResponse.SessionSummary(
                session.getId(),
                effectiveActivityStatus(session.getStatus(), session.getShutdownTime(), device),
                session.getStartupTime(),
                effectiveEndTime(session.getStatus(), session.getShutdownTime(), device),
                effectiveDuration(
                        session.getStatus(),
                        session.getSessionDurationSeconds(),
                        session.getStartupTime(),
                        session.getShutdownTime(),
                        device));
    }

    private DeviceOverviewResponse.IdleSummary toIdleSummary(
            IdleActivity idle, Device device) {
        if (idle == null) {
            return null;
        }
        return new DeviceOverviewResponse.IdleSummary(
                idle.getId(),
                effectiveActivityStatus(idle.getStatus(), idle.getIdleEnd(), device),
                idle.getIdleStart(),
                effectiveEndTime(idle.getStatus(), idle.getIdleEnd(), device),
                effectiveDuration(
                        idle.getStatus(),
                        idle.getIdleSeconds(),
                        idle.getIdleStart(),
                        idle.getIdleEnd(),
                        device));
    }

    private DeviceOverviewResponse.WindowSummary toWindowSummary(
            ActiveWindowActivity window, Device device) {
        if (window == null) {
            return null;
        }
        return new DeviceOverviewResponse.WindowSummary(
                window.getId(),
                window.getWindowTitle(),
                effectiveActivityStatus(window.getStatus(), window.getEndTime(), device),
                window.getStartTime(),
                effectiveEndTime(window.getStatus(), window.getEndTime(), device),
                effectiveDuration(
                        window.getStatus(),
                        window.getDurationSeconds(),
                        window.getStartTime(),
                        window.getEndTime(),
                        device));
    }

    private long effectiveDuration(
            String storedStatus,
            Long storedDuration,
            LocalDateTime start,
            LocalDateTime end,
            Device device) {
        if (start == null) {
            return 0;
        }
        String status = effectiveActivityStatus(storedStatus, end, device);
        LocalDateTime effectiveEnd;
        if ("RUNNING".equals(status)) {
            effectiveEnd = LocalDateTime.now(ZoneOffset.UTC);
        } else if ("OFFLINE".equals(status)) {
            effectiveEnd = device == null || device.getLastSeen() == null
                    ? start
                    : device.getLastSeen();
        } else if (end != null) {
            if (storedDuration != null && storedDuration >= 0) {
                return storedDuration;
            }
            effectiveEnd = end;
        } else {
            return storedDuration == null ? 0 : Math.max(0, storedDuration);
        }
        if (effectiveEnd.isBefore(start)) {
            effectiveEnd = start;
        }
        return Math.max(0, Duration.between(start, effectiveEnd).getSeconds());
    }

    private LocalDateTime effectiveEndTime(
            String storedStatus, LocalDateTime end, Device device) {
        String status = effectiveActivityStatus(storedStatus, end, device);
        return "RUNNING".equals(status) || "OFFLINE".equals(status) ? null : end;
    }

    private String effectiveActivityStatus(
            String storedStatus, LocalDateTime end, Device device) {
        String normalized = safe(storedStatus).toUpperCase(Locale.ROOT);
        if ("INTERRUPTED".equals(normalized)) {
            return "CLOSED";
        }
        if ("OFFLINE".equals(normalized)) {
            if (device != null && !isDeviceOnline(device)) {
                return "OFFLINE";
            }
            return end == null ? "RUNNING" : "CLOSED";
        }
        if ("RUNNING".equals(normalized) && device != null && !isDeviceOnline(device)) {
            return "OFFLINE";
        }
        return normalized.isBlank() ? "CLOSED" : normalized;
    }

    private String queryStatus(Device device, String requestedStatus) {
        if (requestedStatus == null) {
            return null;
        }
        if ("OFFLINE".equals(requestedStatus)) {
            return isDeviceOnline(device) ? "__NONE__" : "RUNNING";
        }
        if ("RUNNING".equals(requestedStatus) && !isDeviceOnline(device)) {
            return "__NONE__";
        }
        return requestedStatus;
    }

    private boolean isDeviceOnline(Device device) {
        if (device == null || !device.isOnline() || device.getLastSeen() == null) {
            return false;
        }
        return !device.getLastSeen().isBefore(onlineCutoff());
    }

    private LocalDateTime onlineCutoff() {
        return LocalDateTime.now(ZoneOffset.UTC)
                .minusSeconds(Math.max(1L, offlineAfterSeconds));
    }

    private ActivityTableResponse<?> emptyTable(String type, int page, int size) {
        return new ActivityTableResponse<>(type, List.of(), 0L, page, size, 0, 0L);
    }


    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record DateRange(LocalDateTime from, LocalDateTime to) {
    }
}
