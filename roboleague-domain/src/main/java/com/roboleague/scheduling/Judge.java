package com.roboleague.scheduling;

import java.util.Objects;

/**
 * Competition judge responsible for officiating and evaluating attempts.
 */
public record Judge(
        String id,
        String fullName,
        String specialty,
        String certificationLevel
) {
    public Judge {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(fullName, "fullName cannot be null");
    }

    public static Judge of(String id, String fullName, String specialty) {
        return new Judge(id, fullName, specialty, "CERTIFIED");
    }
}
