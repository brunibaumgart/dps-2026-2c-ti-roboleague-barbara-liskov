package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.Points;

import java.util.Objects;

/**
 * Value object specifying base score and target duration for a time-based challenge.
 */
public record TimeTargets(Points basePoints, double targetTimeSeconds) {
    public TimeTargets {
        Objects.requireNonNull(basePoints, "basePoints cannot be null");
        if (basePoints.isNegative()) {
            throw new IllegalArgumentException("basePoints cannot be negative");
        }
        if (targetTimeSeconds <= 0) {
            throw new IllegalArgumentException("targetTimeSeconds must be positive");
        }
    }

    public static TimeTargets of(Points basePoints, double targetTimeSeconds) {
        return new TimeTargets(basePoints, targetTimeSeconds);
    }
}
