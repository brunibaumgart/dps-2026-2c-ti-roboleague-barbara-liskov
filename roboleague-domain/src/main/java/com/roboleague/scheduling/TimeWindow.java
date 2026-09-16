package com.roboleague.scheduling;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object representing a scheduled time window.
 */
public record TimeWindow(LocalDateTime startTime, LocalDateTime endTime) {
    public TimeWindow {
        Objects.requireNonNull(startTime, "startTime cannot be null");
        Objects.requireNonNull(endTime, "endTime cannot be null");
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime cannot be before startTime");
        }
    }

    public Duration duration() {
        return Duration.between(startTime, endTime);
    }

    public static TimeWindow of(LocalDateTime startTime, LocalDateTime endTime) {
        return new TimeWindow(startTime, endTime);
    }
}
