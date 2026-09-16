package com.roboleague.evaluation.rules;

/**
 * Value object specifying base score and target duration for a time-based challenge.
 */
public record TimeTargets(double basePoints, double targetTimeSeconds) {
    public TimeTargets {
        if (targetTimeSeconds <= 0) {
            throw new IllegalArgumentException("targetTimeSeconds must be positive");
        }
    }

    public static TimeTargets of(double basePoints, double targetTimeSeconds) {
        return new TimeTargets(basePoints, targetTimeSeconds);
    }
}
