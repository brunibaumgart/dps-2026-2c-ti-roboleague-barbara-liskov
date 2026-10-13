package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.Points;

import java.util.Objects;

/**
 * Victims placed in a rescue track and the deduction for each one left behind.
 */
public record VictimTariff(int totalVictims, Points deductionPerAbandoned) {
    public VictimTariff {
        if (totalVictims < 1) {
            throw new IllegalArgumentException("totalVictims must be positive");
        }
        Objects.requireNonNull(deductionPerAbandoned, "deductionPerAbandoned cannot be null");
        if (deductionPerAbandoned.isNegative()) {
            throw new IllegalArgumentException("deductionPerAbandoned cannot be negative: leaving a victim never adds points");
        }
    }
}
