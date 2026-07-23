package com.tracker.server.agent.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tracker.server.agent.entity.AgentActivity;
import com.tracker.server.agent.model.ActivityKind;
import com.tracker.server.agent.model.ActivityState;
import com.tracker.server.agent.repository.AgentActivityRepository;
import com.tracker.server.entity.ActiveWindowActivity;
import com.tracker.server.entity.DeviceSession;
import com.tracker.server.entity.IdleActivity;
import com.tracker.server.entity.ProcessActivity;
import com.tracker.server.repository.ActiveWindowActivityRepository;
import com.tracker.server.repository.DeviceRepository;
import com.tracker.server.repository.DeviceSessionRepository;
import com.tracker.server.repository.IdleActivityRepository;
import com.tracker.server.repository.ProcessActivityRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Repairs projection duplicates created by older deployments.
 *
 * <p>The revisioned {@code agent_activities} table remains authoritative. This service only
 * merges duplicate dashboard rows and repoints every canonical record to the retained row.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectionDuplicateRepairService {

    private final AgentActivityRepository agentActivityRepository;
    private final DeviceRepository deviceRepository;
    private final ProcessActivityRepository processRepository;
    private final ActiveWindowActivityRepository windowRepository;
    private final IdleActivityRepository idleRepository;
    private final DeviceSessionRepository sessionRepository;
    private final AtomicBoolean repairing = new AtomicBoolean(false);

    @Transactional
    public void repairAll() {
        repair(true);
    }

    @Transactional
    public void repairIncremental() {
        repair(false);
    }

    private void repair(boolean includeHistoricalMixedRows) {
        if (!repairing.compareAndSet(false, true)) {
            return;
        }
        int removed = 0;
        try {
            removed += repairProcessLifecycles(includeHistoricalMixedRows);
            if (includeHistoricalMixedRows) {
                removed += repairProcesses();
                removed += repairWindows();
                removed += repairIdle();
                removed += repairSessions();
            }
            if (removed > 0) {
                log.warn("Merged {} duplicate activity projection row(s)", removed);
            }
        } finally {
            repairing.set(false);
        }
    }

    private int repairProcessLifecycles(boolean includeHistoricalMixedRows) {
        int removed = 0;
        List<Object[]> keys = includeHistoricalMixedRows
                ? processRepository.findRunningKeysWithOtherRows()
                : processRepository.findDuplicateRunningKeys();
        for (Object[] key : keys) {
            Long deviceId = (Long) key[0];
            Long pid = (Long) key[1];
            deviceRepository.findByIdForUpdate(deviceId).orElse(null);

            List<ProcessActivity> rows = processRepository
                    .findByDeviceIdAndPidOrderByStartTimeAscIdAsc(deviceId, pid);
            if (rows.size() < 2) {
                continue;
            }

            removed += mergeNearDuplicateProcessRows(rows);
            processRepository.flush();

            // A PID can legitimately be reused later. Preserve those separate histories,
            // but never leave more than the newest lifecycle RUNNING.
            List<ProcessActivity> remaining = processRepository
                    .findByDeviceIdAndPidOrderByStartTimeAscIdAsc(deviceId, pid);
            interruptOlderRunningRows(remaining);
        }
        processRepository.flush();
        return removed;
    }

    private int mergeNearDuplicateProcessRows(List<ProcessActivity> rows) {
        int removed = 0;
        boolean[] consumed = new boolean[rows.size()];

        for (int index = 0; index < rows.size(); index++) {
            if (consumed[index]) {
                continue;
            }
            ProcessActivity base = rows.get(index);
            List<ProcessActivity> cluster = new ArrayList<>();
            cluster.add(base);
            consumed[index] = true;

            for (int candidateIndex = index + 1; candidateIndex < rows.size(); candidateIndex++) {
                if (consumed[candidateIndex]) {
                    continue;
                }
                ProcessActivity candidate = rows.get(candidateIndex);
                if (sameProcessName(base, candidate) && startsWithinDuplicateWindow(base, candidate)) {
                    cluster.add(candidate);
                    consumed[candidateIndex] = true;
                }
            }

            if (cluster.size() < 2) {
                continue;
            }

            ProcessActivity keeper = mergeProcessCluster(cluster);
            for (ProcessActivity duplicate : cluster) {
                if (Objects.equals(duplicate.getId(), keeper.getId())) {
                    continue;
                }
                agentActivityRepository.repointLegacyRecord(
                        ActivityKind.PROCESS, duplicate.getId(), keeper.getId());
                processRepository.delete(duplicate);
                removed++;
            }
            processRepository.save(keeper);
        }
        return removed;
    }

    private ProcessActivity mergeProcessCluster(List<ProcessActivity> rows) {
        List<Long> ids = rows.stream()
                .map(ProcessActivity::getId)
                .filter(Objects::nonNull)
                .toList();
        List<AgentActivity> canonical = ids.isEmpty()
                ? List.of()
                : agentActivityRepository.findByKindAndLegacyRecordIdIn(
                        ActivityKind.PROCESS, ids);
        AgentActivity authoritative = canonical.stream()
                .max(Comparator
                        .comparing(AgentActivity::getUpdatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparingLong(AgentActivity::getRevision))
                .orElse(null);

        ProcessActivity keeper = authoritative == null
                ? rows.stream().max(Comparator.comparing(
                        ProcessActivity::getId,
                        Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(rows.getFirst())
                : rows.stream()
                        .filter(row -> Objects.equals(
                                row.getId(), authoritative.getLegacyRecordId()))
                        .findFirst()
                        .orElseGet(() -> rows.stream().max(Comparator.comparing(
                                ProcessActivity::getId,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                                .orElse(rows.getFirst()));

        LocalDateTime earliestStart = rows.stream()
                .map(ProcessActivity::getStartTime)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(keeper.getStartTime());
        keeper.setStartTime(earliestStart);

        if (authoritative == null) {
            mergeProcessState(keeper, rows);
            return keeper;
        }

        if (authoritative.getProcessName() != null
                && !authoritative.getProcessName().isBlank()) {
            keeper.setProcessName(authoritative.getProcessName());
        }
        if (authoritative.getState() == ActivityState.OPEN) {
            keeper.setStatus("RUNNING");
            keeper.setEndTime(null);
            keeper.setDurationSeconds(null);
            return keeper;
        }

        LocalDateTime end = authoritative.getEndedAt();
        if (end != null && earliestStart != null && end.isBefore(earliestStart)) {
            end = null;
        }
        keeper.setEndTime(end);
        keeper.setDurationSeconds(end == null || earliestStart == null
                ? null
                : Math.max(0L, Duration.between(earliestStart, end).toSeconds()));
        keeper.setStatus(authoritative.getState() == ActivityState.INFERRED
                ? "INTERRUPTED"
                : "CLOSED");
        return keeper;
    }

    private void interruptOlderRunningRows(List<ProcessActivity> rows) {
        List<ProcessActivity> running = rows.stream()
                .filter(row -> "RUNNING".equalsIgnoreCase(row.getStatus()))
                .sorted(Comparator
                        .comparing(ProcessActivity::getStartTime,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ProcessActivity::getId,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        if (running.size() < 2) {
            return;
        }

        ProcessActivity newest = running.getLast();
        LocalDateTime newStart = newest.getStartTime();
        for (int index = 0; index < running.size() - 1; index++) {
            ProcessActivity older = running.get(index);
            LocalDateTime end = older.getStartTime() != null
                            && newStart != null
                            && !newStart.isBefore(older.getStartTime())
                    ? newStart
                    : null;
            older.setEndTime(end);
            older.setDurationSeconds(end == null || older.getStartTime() == null
                    ? null
                    : Math.max(0L, Duration.between(
                            older.getStartTime(), end).toSeconds()));
            older.setStatus("INTERRUPTED");
            processRepository.save(older);
        }
    }

    private static boolean sameProcessName(ProcessActivity left, ProcessActivity right) {
        String leftName = left.getProcessName() == null
                ? null
                : left.getProcessName().toLowerCase(Locale.ROOT);
        String rightName = right.getProcessName() == null
                ? null
                : right.getProcessName().toLowerCase(Locale.ROOT);
        return Objects.equals(leftName, rightName);
    }

    private static boolean startsWithinDuplicateWindow(
            ProcessActivity left, ProcessActivity right) {
        if (left.getStartTime() == null || right.getStartTime() == null) {
            return left.getStartTime() == null && right.getStartTime() == null;
        }
        return Math.abs(Duration.between(
                left.getStartTime(), right.getStartTime()).toMillis()) <= 10_000L;
    }

    private int repairProcesses() {
        int removed = 0;
        for (Object[] key : processRepository.findDuplicateNaturalKeys()) {
            Long deviceId = (Long) key[0];
            deviceRepository.findByIdForUpdate(deviceId).orElse(null);
            Long pid = (Long) key[1];
            LocalDateTime start = (LocalDateTime) key[2];
            List<ProcessActivity> rows = processRepository
                    .findByDeviceIdAndPidAndStartTimeOrderByIdDesc(deviceId, pid, start);
            if (rows.size() < 2) {
                continue;
            }
            ProcessActivity keeper = mergeProcessCluster(rows);
            for (ProcessActivity duplicate : rows) {
                if (Objects.equals(duplicate.getId(), keeper.getId())) {
                    continue;
                }
                agentActivityRepository.repointLegacyRecord(
                        ActivityKind.PROCESS, duplicate.getId(), keeper.getId());
                processRepository.delete(duplicate);
                removed++;
            }
            processRepository.save(keeper);
        }
        return removed;
    }

    private int repairWindows() {
        int removed = 0;
        for (Object[] key : windowRepository.findDuplicateNaturalKeys()) {
            Long deviceId = (Long) key[0];
            deviceRepository.findByIdForUpdate(deviceId).orElse(null);
            LocalDateTime start = (LocalDateTime) key[1];
            String title = (String) key[2];
            List<ActiveWindowActivity> rows = windowRepository
                    .findByDeviceIdAndStartTimeAndWindowTitleOrderByIdDesc(deviceId, start, title);
            if (rows.size() < 2) {
                continue;
            }
            ActiveWindowActivity keeper = rows.getFirst();
            mergeWindowState(keeper, rows);
            for (int index = 1; index < rows.size(); index++) {
                ActiveWindowActivity duplicate = rows.get(index);
                agentActivityRepository.repointLegacyRecord(
                        ActivityKind.ACTIVE_WINDOW, duplicate.getId(), keeper.getId());
                windowRepository.delete(duplicate);
                removed++;
            }
            windowRepository.save(keeper);
        }
        return removed;
    }

    private int repairIdle() {
        int removed = 0;
        for (Object[] key : idleRepository.findDuplicateNaturalKeys()) {
            Long deviceId = (Long) key[0];
            deviceRepository.findByIdForUpdate(deviceId).orElse(null);
            LocalDateTime start = (LocalDateTime) key[1];
            List<IdleActivity> rows = idleRepository
                    .findByDeviceIdAndIdleStartOrderByIdDesc(deviceId, start);
            if (rows.size() < 2) {
                continue;
            }
            IdleActivity keeper = rows.getFirst();
            mergeIdleState(keeper, rows);
            for (int index = 1; index < rows.size(); index++) {
                IdleActivity duplicate = rows.get(index);
                agentActivityRepository.repointLegacyRecord(
                        ActivityKind.IDLE, duplicate.getId(), keeper.getId());
                idleRepository.delete(duplicate);
                removed++;
            }
            idleRepository.save(keeper);
        }
        return removed;
    }

    private int repairSessions() {
        int removed = 0;
        for (Object[] key : sessionRepository.findDuplicateNaturalKeys()) {
            Long deviceId = (Long) key[0];
            deviceRepository.findByIdForUpdate(deviceId).orElse(null);
            LocalDateTime start = (LocalDateTime) key[1];
            List<DeviceSession> rows = sessionRepository
                    .findByDeviceIdAndStartupTimeOrderByIdDesc(deviceId, start);
            if (rows.size() < 2) {
                continue;
            }
            DeviceSession keeper = rows.getFirst();
            mergeSessionState(keeper, rows);
            for (int index = 1; index < rows.size(); index++) {
                DeviceSession duplicate = rows.get(index);
                agentActivityRepository.repointLegacyRecord(
                        ActivityKind.DEVICE_SESSION, duplicate.getId(), keeper.getId());
                sessionRepository.delete(duplicate);
                removed++;
            }
            sessionRepository.save(keeper);
        }
        return removed;
    }

    private static void mergeProcessState(ProcessActivity keeper, List<ProcessActivity> rows) {
        if (hasRunning(rows.stream().map(ProcessActivity::getStatus).toList())) {
            keeper.setStatus("RUNNING");
            keeper.setEndTime(null);
            keeper.setDurationSeconds(null);
            return;
        }
        ProcessActivity winner = rows.stream()
                .max(Comparator.comparing(
                        ProcessActivity::getEndTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(keeper);
        keeper.setEndTime(winner.getEndTime());
        keeper.setDurationSeconds(winner.getDurationSeconds());
        keeper.setStatus(winner.getStatus());
        if (keeper.getProcessName() == null) {
            keeper.setProcessName(winner.getProcessName());
        }
        if (keeper.getUser() == null) {
            keeper.setUser(winner.getUser());
        }
    }

    private static void mergeWindowState(
            ActiveWindowActivity keeper, List<ActiveWindowActivity> rows) {
        if (hasRunning(rows.stream().map(ActiveWindowActivity::getStatus).toList())) {
            keeper.setStatus("RUNNING");
            keeper.setEndTime(null);
            keeper.setDurationSeconds(null);
            return;
        }
        ActiveWindowActivity winner = rows.stream()
                .max(Comparator.comparing(
                        ActiveWindowActivity::getEndTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(keeper);
        keeper.setEndTime(winner.getEndTime());
        keeper.setDurationSeconds(winner.getDurationSeconds());
        keeper.setStatus(winner.getStatus());
        if (keeper.getOfflineId() == null) {
            keeper.setOfflineId(winner.getOfflineId());
        }
    }

    private static void mergeIdleState(IdleActivity keeper, List<IdleActivity> rows) {
        if (hasRunning(rows.stream().map(IdleActivity::getStatus).toList())) {
            keeper.setStatus("RUNNING");
            keeper.setIdleEnd(null);
            keeper.setIdleSeconds(null);
            return;
        }
        IdleActivity winner = rows.stream()
                .max(Comparator.comparing(
                        IdleActivity::getIdleEnd,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(keeper);
        keeper.setIdleEnd(winner.getIdleEnd());
        keeper.setIdleSeconds(winner.getIdleSeconds());
        keeper.setStatus(winner.getStatus());
        if (keeper.getUser() == null) {
            keeper.setUser(winner.getUser());
        }
    }

    private static void mergeSessionState(DeviceSession keeper, List<DeviceSession> rows) {
        if (hasRunning(rows.stream().map(DeviceSession::getStatus).toList())) {
            keeper.setStatus("RUNNING");
            keeper.setShutdownTime(null);
            keeper.setSessionDurationSeconds(null);
            return;
        }
        DeviceSession winner = rows.stream()
                .max(Comparator.comparing(
                        DeviceSession::getShutdownTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(keeper);
        keeper.setShutdownTime(winner.getShutdownTime());
        keeper.setSessionDurationSeconds(winner.getSessionDurationSeconds());
        keeper.setStatus(winner.getStatus());
        if (keeper.getUser() == null) {
            keeper.setUser(winner.getUser());
        }
    }

    private static boolean hasRunning(List<String> statuses) {
        return statuses.stream().anyMatch(status -> "RUNNING".equalsIgnoreCase(status));
    }
}
