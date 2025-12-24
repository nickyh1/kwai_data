package com.example.kwai_data.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

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
    }
}

