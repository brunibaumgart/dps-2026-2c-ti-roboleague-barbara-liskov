package com.roboleague.support;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class SystemClockTest {
    @Test
    void calendarDateUsesTheConfiguredZoneRatherThanTheHostZone() {
        Instant instant = Instant.parse("2026-10-09T01:30:00Z");
        SystemClock argentina = new SystemClock(java.time.Clock.fixed(instant,
                ZoneId.of("America/Argentina/Buenos_Aires")));
        SystemClock utc = new SystemClock(java.time.Clock.fixed(instant, ZoneId.of("UTC")));

        assertThat(argentina.now()).isEqualTo(LocalDateTime.of(2026, 10, 8, 22, 30));
        assertThat(argentina.today()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(utc.today()).isEqualTo(LocalDate.of(2026, 10, 9));
    }
}
