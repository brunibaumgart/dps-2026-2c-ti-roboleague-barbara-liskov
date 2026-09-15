package com.roboleague.tournament;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Technical specification of a robot entered into a competition.
 */
public record RobotSpecification(
        double weightGrams,
        double lengthMm,
        double widthMm,
        double heightMm,
        int actuatorCount,
        Set<String> sensors
) {
    public RobotSpecification {
        if (weightGrams <= 0) {
            throw new IllegalArgumentException("weightGrams must be positive");
        }
        if (lengthMm <= 0 || widthMm <= 0 || heightMm <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
        if (actuatorCount < 0) {
            throw new IllegalArgumentException("actuatorCount cannot be negative");
        }
        sensors = sensors != null ? Collections.unmodifiableSet(sensors) : Collections.emptySet();
    }
}
