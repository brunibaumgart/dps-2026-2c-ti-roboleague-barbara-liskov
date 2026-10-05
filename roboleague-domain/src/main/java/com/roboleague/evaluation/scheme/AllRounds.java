package com.roboleague.evaluation.scheme;

import java.util.List;

public final class AllRounds implements RoundSelection {

    @Override
    public ChallengeScore select(List<RoundScore> rounds) {
        return new ChallengeScore(rounds, List.of(), describe());
    }

    @Override
    public String describe() {
        return "todas las rondas";
    }
}
