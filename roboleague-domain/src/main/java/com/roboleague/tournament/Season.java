package com.roboleague.tournament;

import java.util.Objects;

/**
 * Annual or periodic season of competitions.
 */
public record Season(
        String id,
        int year,
        String name
) {
    public Season {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid season year: " + year);
        }
    }
}
