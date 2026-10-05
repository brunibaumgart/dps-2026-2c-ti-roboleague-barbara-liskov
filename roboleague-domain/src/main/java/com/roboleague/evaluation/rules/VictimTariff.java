package com.roboleague.evaluation.rules;

/**
 * Victims placed in a rescue track, points for each one rescued and the deduction for each one left behind.
 */
public record VictimTariff(int totalVictims, double pointsPerRescued, double deductionPerAbandoned) {
    public VictimTariff {
        if (totalVictims < 1) {
            throw new IllegalArgumentException("totalVictims must be positive");
        }
        if (pointsPerRescued < 0) {
            throw new IllegalArgumentException("pointsPerRescued cannot be negative");
        }
        if (deductionPerAbandoned < 0) {
            throw new IllegalArgumentException("deductionPerAbandoned cannot be negative: leaving a victim never adds points");
        }
    }
}
