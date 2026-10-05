package com.roboleague.evaluation.scheme;

public final class FewerPenalties implements TieBreakCriterion {

    @Override
    public String code() {
        return "fewer-penalties";
    }

    @Override
    public String name() {
        return "Menos faltas";
    }

    @Override
    public int compare(ChallengeScore a, ChallengeScore b) {
        return Integer.compare(a.penalties(), b.penalties());
    }
}
