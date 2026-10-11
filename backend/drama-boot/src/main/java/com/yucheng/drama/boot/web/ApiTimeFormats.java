package com.yucheng.drama.boot.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/** Shared wire formats for JSON bodies and Spring MVC request parameters. */
public final class ApiTimeFormats {
    private static final Pattern INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}"
                    + "(?:\\.[0-9]{1,6})?(?:Z|[+-][0-9]{2}:[0-9]{2})");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    private ApiTimeFormats() {
    }

    public static Instant parseInstant(String value) {
        if (!INSTANT.matcher(value).matches()) {
            throw new IllegalArgumentException("Expected a timestamp with Z or an offset and at most 6 fractional digits.");
        }
        return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
    }

    public static String formatInstant(Instant value) {
        return DateTimeFormatter.ISO_INSTANT.format(value);
    }

    public static LocalDate parseDate(String value) {
        if (!DATE.matcher(value).matches()) {
            throw new IllegalArgumentException("Expected a date in yyyy-MM-dd format.");
        }
        return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public static String formatDate(LocalDate value) {
        return DateTimeFormatter.ISO_LOCAL_DATE.format(value);
    }
}
