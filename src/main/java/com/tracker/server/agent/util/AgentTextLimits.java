package com.tracker.server.agent.util;

/** Final server-side text limits matching the database schema. */
public final class AgentTextLimits {

    public static final int PROCESS_NAME_CODE_POINTS = 512;
    public static final int WINDOW_TITLE_CODE_POINTS = 1000;

    private AgentTextLimits() {
    }

    public static String processName(String value) {
        return truncateCodePoints(value, PROCESS_NAME_CODE_POINTS);
    }

    public static String windowTitle(String value) {
        return truncateCodePoints(value, WINDOW_TITLE_CODE_POINTS);
    }

    public static String truncateCodePoints(String value, int maximumCodePoints) {
        if (value == null || maximumCodePoints < 0) {
            return value;
        }
        int count = value.codePointCount(0, value.length());
        if (count <= maximumCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maximumCodePoints));
    }
}
