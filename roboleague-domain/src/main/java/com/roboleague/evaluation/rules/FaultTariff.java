package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.Points;

import java.util.Objects;

/**
 * How many faults are tolerated before deducting, and how much each extra fault costs.
 */
public record FaultTariff(int freeAllowance, Points deductionPerFault) {
    public FaultTariff {
        if (freeAllowance < 0) {
            throw new IllegalArgumentException("freeAllowance cannot be negative");
        }
        Objects.requireNonNull(deductionPerFault, "deductionPerFault cannot be null");
        if (deductionPerFault.isNegative()) {
            throw new IllegalArgumentException("deductionPerFault cannot be negative: a fault never adds points");
        }
    }
}
