package com.moodrecipe.backend.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Single business clock for user-facing dates and daily quotas. */
public final class AppClock {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private AppClock() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
