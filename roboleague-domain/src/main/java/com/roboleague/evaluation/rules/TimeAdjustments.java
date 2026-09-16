package com.roboleague.evaluation.rules;

/**
 * Value object specifying bonuses and deductions per unit time.
 */
public record TimeAdjustments(
        double pointsPerSecondUnder,
        double deductionPerSecondOver,
        double minPoints
) {
    public static TimeAdjustments of(double pointsPerSecondUnder, double deductionPerSecondOver, double minPoints) {
        return new TimeAdjustments(pointsPerSecondUnder, deductionPerSecondOver, minPoints);
    }
}
