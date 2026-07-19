package com.tracker.server.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class DateTimeUtil {

    private DateTimeUtil() {
    }

    public static LocalDateTime now() {

        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
