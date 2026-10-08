package com.roboleague.support;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

public final class SystemClock implements Clock {
    private final java.time.Clock clock;

    public SystemClock(ZoneId zone) {
        this(java.time.Clock.system(Objects.requireNonNull(zone, "zone cannot be null")));
    }

    public SystemClock(java.time.Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
