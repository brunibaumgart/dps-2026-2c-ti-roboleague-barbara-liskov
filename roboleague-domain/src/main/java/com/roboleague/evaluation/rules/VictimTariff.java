package com.roboleague.evaluation.rules;

/**
 * Victims placed in a rescue track and the deduction for each one left behind.
 */
public record VictimTariff(int totalVictims, double deductionPerAbandoned) {
    public VictimTariff {
        if (totalVictims < 1) {
            throw new IllegalArgumentException("totalVictims must be positive");
        }
        if (deductionPerAbandoned < 0) {
            throw new IllegalArgumentException("deductionPerAbandoned cannot be negative: leaving a victim never adds points");
        }
    }
}
