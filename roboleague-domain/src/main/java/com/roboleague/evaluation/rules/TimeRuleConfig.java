package com.roboleague.evaluation.rules;

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

    public static TimeRuleConfig of(double basePoints, double targetTimeSeconds,
                                    double pointsPerSecondUnder, double deductionPerSecondOver, double minPoints) {
        return new TimeRuleConfig(
                new TimeTargets(basePoints, targetTimeSeconds),
                new TimeAdjustments(pointsPerSecondUnder, deductionPerSecondOver, minPoints)
        );
    }
}
