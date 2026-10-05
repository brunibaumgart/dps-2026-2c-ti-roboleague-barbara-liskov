package com.roboleague.evaluation.scheme;

/**
 * Best (lowest) time among the considered rounds wins. A team without rounds has no time and goes last.
 */
public final class LowerTime implements TieBreakCriterion {

    @Override
    public String name() {
        return "Menor tiempo";
    }

    @Override
    public int compare(ChallengeScore a, ChallengeScore b) {
        return MissingGoesLast.lowerFirst(a.bestTime(), b.bestTime());
    }
}
