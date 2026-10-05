package com.roboleague.evaluation.scheme;

import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.StrategyDefinition;

import java.util.List;

public final class AllRounds implements RoundSelection {

    public static final String TYPE = "all-rounds";

    @Override
    public StrategyDefinition definition() {
        return new StrategyDefinition(TYPE, Parameters.none());
    }

    @Override
    public ChallengeScore select(List<RoundScore> rounds) {
        return new ChallengeScore(rounds, List.of(), describe());
    }

    @Override
    public String describe() {
        return "todas las rondas";
    }
}
