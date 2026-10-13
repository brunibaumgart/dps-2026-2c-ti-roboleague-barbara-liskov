package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.Points;

import java.util.Objects;

/**
 * Value object grouping all parameters for a time-based scoring rule.
 */
public record TimeRuleConfig(TimeTargets targets, TimeAdjustments adjustments) {
    public TimeRuleConfig {
        Objects.requireNonNull(targets, "targets cannot be null");
        Objects.requireNonNull(adjustments, "adjustments cannot be null");
    }

    public static TimeRuleConfig of(TimeTargets targets, TimeAdjustments adjustments) {
        return new TimeRuleConfig(targets, adjustments);
    }

    public static TimeRuleConfig of(Points basePoints, double targetTimeSeconds,
                                    double pointsPerSecondUnder, double deductionPerSecondOver, Points minPoints) {
        return new TimeRuleConfig(
                new TimeTargets(basePoints, targetTimeSeconds),
                new TimeAdjustments(pointsPerSecondUnder, deductionPerSecondOver, minPoints)
        );
    }
}
