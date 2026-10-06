package com.roboleague.evaluation.scheme;

/**
 * Fewer points taken away by deductions in the considered rounds wins, whatever the deductions count: faults, line
 * exits, collisions or consumption. A team without rounds has nothing deducted to compare and goes last.
 */
public final class LowerDeductions implements TieBreakCriterion {

    @Override
    public String code() {
        return "lower-deductions";
    }

    @Override
    public String name() {
        return "Menor descuento por penalizaciones";
    }

    @Override
    public int compare(ChallengeScore a, ChallengeScore b) {
        return MissingGoesLast.lowerFirst(a.deducted(), b.deducted());
    }
}
