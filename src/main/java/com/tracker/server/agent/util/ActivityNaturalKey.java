package com.tracker.server.agent.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

import com.tracker.server.agent.entity.AgentActivity;
import com.tracker.server.agent.model.ActivityKind;

public final class ActivityNaturalKey {

    private ActivityNaturalKey() {
    }

    public static String of(AgentActivity activity) {
        String source = switch (activity.getKind()) {
            case PROCESS -> join(
                    activity.getProcessId(),
                    normalized(activity.getProcessName()),
                    activity.getStartedAt());
            case ACTIVE_WINDOW -> join(
                    activity.getProcessId(),
                    normalized(activity.getProcessName()),
                    activity.getWindowTitle(),
                    activity.getStartedAt());
            case IDLE, DEVICE_SESSION -> join(activity.getStartedAt());
        };
        return sha256(activity.getKind().name() + "|" + source);
    }

    public static String legacy(
            ActivityKind kind,
            Long processId,
            String processName,
            String windowTitle,
            LocalDateTime startedAt) {
        String source = switch (kind) {
            case PROCESS -> join(processId, normalized(processName), startedAt);
            case ACTIVE_WINDOW -> join(
                    processId, normalized(processName), windowTitle, startedAt);
            case IDLE, DEVICE_SESSION -> join(startedAt);
        };
        return sha256(kind.name() + "|" + source);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String join(Object... values) {
        StringBuilder builder = new StringBuilder();
        for (Object value : values) {
            if (!builder.isEmpty()) {
                builder.append('|');
            }
            builder.append(value == null ? "" : value);
        }
        return builder.toString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create activity natural key", ex);
        }
    }
}
