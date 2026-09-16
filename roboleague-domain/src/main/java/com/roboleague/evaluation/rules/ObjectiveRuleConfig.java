package com.roboleague.evaluation.rules;

/**
 * Value object configuring objective points and completion bonuses.
 */
public record ObjectiveRuleConfig(
        double pointsPerObjective,
        int totalPossibleObjectives,
        double allCompletedBonus
) {
    public ObjectiveRuleConfig {
        if (pointsPerObjective < 0) {
            throw new IllegalArgumentException("pointsPerObjective cannot be negative");
        }
        if (totalPossibleObjectives < 0) {
            throw new IllegalArgumentException("totalPossibleObjectives cannot be negative");
        }
        if (allCompletedBonus < 0) {
            throw new IllegalArgumentException("allCompletedBonus cannot be negative");
        }
    }

    public static ObjectiveRuleConfig of(double pointsPerObjective, int totalPossibleObjectives, double allCompletedBonus) {
        return new ObjectiveRuleConfig(pointsPerObjective, totalPossibleObjectives, allCompletedBonus);
    }
}
