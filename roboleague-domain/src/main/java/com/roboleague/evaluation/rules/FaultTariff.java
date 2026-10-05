package com.roboleague.evaluation.rules;

/**
 * How many faults are tolerated before deducting, and how much each extra fault costs.
 */
public record FaultTariff(int freeAllowance, double deductionPerFault) {
    public FaultTariff {
        if (freeAllowance < 0) {
            throw new IllegalArgumentException("freeAllowance cannot be negative");
        }
        if (deductionPerFault < 0) {
            throw new IllegalArgumentException("deductionPerFault cannot be negative: a fault never adds points");
        }
    }
}
