package com.example.kwai_data.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TimeRangeProvider {

    private final KwaiTimeProperties props;
    private final Clock clock; // 单独提供一个 Clock Bean



    public TimeRangeMillis last7DaysInclusive() {
        ZoneId zoneId = ZoneId.of(props.getZone());
        LocalDate today = LocalDate.now(clock.withZone(zoneId));

        Instant start = today.minusDays(props.getLookbackDays()).atStartOfDay(zoneId).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zoneId).toInstant().minusMillis(1);

        return new TimeRangeMillis(start.toEpochMilli(), end.toEpochMilli());
    }//近七天的

    public TimeRangeMillis yesterdayToTodayStart() {
        ZoneId zoneId = ZoneId.of(props.getZone());
        LocalDate today = LocalDate.now(clock.withZone(zoneId));

        Instant start = today.minusDays(1).atStartOfDay(zoneId).toInstant(); // 昨天 00:00
        Instant end = today.atStartOfDay(zoneId).toInstant();                // 今天 00:00

        return new TimeRangeMillis(start.toEpochMilli(), end.toEpochMilli());
    }//昨天一天的

    public TimeRangeMillis monthStartToTodayStart() {
        ZoneId zoneId = ZoneId.of(props.getZone());
        LocalDate today = LocalDate.now(clock.withZone(zoneId));

        LocalDate monthStartDate = today.withDayOfMonth(1);

        Instant start = monthStartDate.atStartOfDay(zoneId).toInstant(); // 当月1号 00:00
        Instant end = today.atStartOfDay(zoneId).toInstant();            // 今天 00:00

        return new TimeRangeMillis(start.toEpochMilli(), end.toEpochMilli());
    }//本月到今天00：00的

    public List<TimeRangeMillis> splitByDays(TimeRangeMillis range, int chunkDays, EndMsMode mode) {
        ZoneId zoneId = ZoneId.of(props.getZone());

        Instant start = Instant.ofEpochMilli(range.getStartMs());
        Instant end = Instant.ofEpochMilli(range.getEndMs());


        // 把输入统一成半开区间 [start, endExclusive)
        Instant endExclusive = (mode == EndMsMode.INCLUSIVE) ? end.plusMillis(1) : end;

        LocalDate startDate = start.atZone(zoneId).toLocalDate();
        LocalDate startDateInclusive = start.atZone(zoneId).toLocalDate();

        ZonedDateTime zEnd = endExclusive.atZone(zoneId);
        LocalDate endDateExclusive = zEnd.toLocalDate();
        if (!zEnd.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
            endDateExclusive = endDateExclusive.plusDays(1);
        }

        return splitByDays(startDateInclusive, endDateExclusive, chunkDays);
    }

    public TimeRangeMillis monthStartToNow() {
        ZoneId zoneId = ZoneId.of(props.getZone());
        LocalDate today = LocalDate.now(clock.withZone(zoneId));

        Instant start = today.withDayOfMonth(1).atStartOfDay(zoneId).toInstant(); // 当月1号 00:00
        Instant end = Instant.now(clock);                                         // 现在（当前时刻）

        System.out.println(end);
        return new TimeRangeMillis(start.toEpochMilli(), end.toEpochMilli());
    }//本月到现在

    public List<TimeRangeMillis> splitByDays(LocalDate startDateInclusive,
                                             LocalDate endDateExclusive,
                                             int chunkDays) {
        ZoneId zoneId = ZoneId.of(props.getZone());

        List<TimeRangeMillis> ranges = new ArrayList<>();
        LocalDate cursor = startDateInclusive;

        while (cursor.isBefore(endDateExclusive)) {
            LocalDate chunkEndExclusive = cursor.plusDays(chunkDays);
            if (chunkEndExclusive.isAfter(endDateExclusive)) {
                chunkEndExclusive = endDateExclusive;
            }

            long startMs = cursor.atStartOfDay(zoneId).toInstant().toEpochMilli();
            long endExclusiveMs = chunkEndExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli();
            long endInclusiveMs = endExclusiveMs - 1; // 关键：转成 API 的“包含式 end”

            ranges.add(new TimeRangeMillis(startMs, endInclusiveMs));

            cursor = chunkEndExclusive;
        }

        return ranges;
    }
}

