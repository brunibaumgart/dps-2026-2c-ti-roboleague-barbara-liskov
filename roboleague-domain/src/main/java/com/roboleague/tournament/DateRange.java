package com.roboleague.tournament;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Value object representing a competition date window.
 */
public record DateRange(LocalDate startDate, LocalDate endDate) {
    public DateRange {
        Objects.requireNonNull(startDate, "startDate cannot be null");
        Objects.requireNonNull(endDate, "endDate cannot be null");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
    }

    public boolean includes(LocalDate date) {
        Objects.requireNonNull(date, "date cannot be null");
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public static DateRange of(LocalDate startDate, LocalDate endDate) {
        return new DateRange(startDate, endDate);
    }
}
