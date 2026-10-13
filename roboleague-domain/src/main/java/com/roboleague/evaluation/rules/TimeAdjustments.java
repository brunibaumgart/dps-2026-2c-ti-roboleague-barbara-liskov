package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.Points;

import java.util.Objects;

/**
 * Value object specifying bonuses and deductions per unit time. The per-second amounts are rates that multiply a
 * time; the minimum is an amount of points.
 */
public record TimeAdjustments(
        double pointsPerSecondUnder,
        double deductionPerSecondOver,
        Points minPoints
) {
    public TimeAdjustments {
        if (pointsPerSecondUnder < 0) {
            throw new IllegalArgumentException("pointsPerSecondUnder cannot be negative");
        }
        if (deductionPerSecondOver < 0) {
            throw new IllegalArgumentException("deductionPerSecondOver cannot be negative: being slower never adds points");
        }
        Objects.requireNonNull(minPoints, "minPoints cannot be null");
        if (minPoints.isNegative()) {
            throw new IllegalArgumentException("minPoints cannot be negative");
        }
    }

    public static TimeAdjustments of(double pointsPerSecondUnder, double deductionPerSecondOver, Points minPoints) {
        return new TimeAdjustments(pointsPerSecondUnder, deductionPerSecondOver, minPoints);
    }
}
