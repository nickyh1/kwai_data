package com.example.kwai_data.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {

    private static final DateTimeFormatter DEFAULT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeUtil() {}

    /** epochMillis -> ZonedDateTime（带时区，推荐） */
    public static ZonedDateTime toZonedDateTime(long epochMillis, String zone) {
        ZoneId zoneId = ZoneId.of(zone);
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId);
    }

    /** epochMillis -> 格式化字符串（yyyy-MM-dd HH:mm:ss） */
    public static String toDateTimeString(long epochMillis, String zone) {
        return toZonedDateTime(epochMillis, zone).format(DEFAULT_FMT);
    }
}

