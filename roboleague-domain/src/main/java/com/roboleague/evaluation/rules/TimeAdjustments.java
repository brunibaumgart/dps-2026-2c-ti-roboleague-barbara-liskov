package com.roboleague.evaluation.rules;

/**
 * Value object specifying bonuses and deductions per unit time.
 */
public record TimeAdjustments(
        double pointsPerSecondUnder,
        double deductionPerSecondOver,
        double minPoints
) {
    public TimeAdjustments {
        if (pointsPerSecondUnder < 0) {
            throw new IllegalArgumentException("pointsPerSecondUnder cannot be negative");
        }
        if (deductionPerSecondOver < 0) {
            throw new IllegalArgumentException("deductionPerSecondOver cannot be negative: being slower never adds points");
        }
        if (minPoints < 0) {
            throw new IllegalArgumentException("minPoints cannot be negative");
        }
    }

    public static TimeAdjustments of(double pointsPerSecondUnder, double deductionPerSecondOver, double minPoints) {
        return new TimeAdjustments(pointsPerSecondUnder, deductionPerSecondOver, minPoints);
    }
}
