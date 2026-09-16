package com.roboleague.scheduling;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object representing the temporal parameters for scheduling a round.
 */
public record RoundScheduleTiming(
        LocalDateTime roundStart,
        Duration slotDuration,
        Duration intervalBetweenSlots
) {
    public RoundScheduleTiming {
        Objects.requireNonNull(roundStart, "roundStart cannot be null");
        Objects.requireNonNull(slotDuration, "slotDuration cannot be null");
        Objects.requireNonNull(intervalBetweenSlots, "intervalBetweenSlots cannot be null");
        if (slotDuration.isNegative() || slotDuration.isZero()) {
            throw new IllegalArgumentException("slotDuration must be positive");
        }
        if (intervalBetweenSlots.isNegative()) {
            throw new IllegalArgumentException("intervalBetweenSlots cannot be negative");
        }
    }

    public static RoundScheduleTiming of(LocalDateTime roundStart, Duration slotDuration, Duration intervalBetweenSlots) {
        return new RoundScheduleTiming(roundStart, slotDuration, intervalBetweenSlots);
    }
}
