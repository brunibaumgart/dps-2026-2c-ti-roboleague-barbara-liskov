package com.roboleague.evaluation.scheme;

public final class HigherTotal implements TieBreakCriterion {

    @Override
    public String code() {
        return "higher-total";
    }

    @Override
    public String name() {
        return "Mayor puntaje total";
    }

    @Override
    public int compare(ChallengeScore a, ChallengeScore b) {
        return Double.compare(b.total(), a.total());
    }
}
