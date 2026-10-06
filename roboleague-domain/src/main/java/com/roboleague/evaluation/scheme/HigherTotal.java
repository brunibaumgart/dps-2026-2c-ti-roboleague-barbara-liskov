package com.roboleague.evaluation.scheme;

public final class HigherTotal implements TieBreakCriterion {

    public static final String CODE = "higher-total";

    @Override
    public String code() {
        return CODE;
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
