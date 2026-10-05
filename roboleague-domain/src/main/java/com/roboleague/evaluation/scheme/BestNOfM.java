package com.roboleague.evaluation.scheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Counts only the best {@code considered} of the {@code outOf} rounds of the challenge (F1).
 * Ties between rounds keep the earlier one. With fewer rounds than {@code considered}, all of them count.
 */
public record BestNOfM(int considered, int outOf) implements RoundSelection {
    public BestNOfM {
        if (considered < 1 || considered > outOf) {
            throw new IllegalArgumentException("considered must be between 1 and outOf: " + considered + " of " + outOf);
        }
    }

    @Override
    public ChallengeScore select(List<RoundScore> rounds) {
        if (rounds.size() > outOf) {
            throw new IllegalArgumentException("a team cannot have more than " + outOf + " rounds: " + rounds.size());
        }
        if (rounds.stream().map(RoundScore::roundId).distinct().count() < rounds.size()) {
            throw new IllegalArgumentException("a team cannot have two scores for the same round");
        }
        Set<RoundScore> best = Collections.newSetFromMap(new IdentityHashMap<>());
        rounds.stream()
                .sorted(Comparator.comparingDouble(RoundScore::total).reversed())
                .limit(considered)
                .forEach(best::add);
        List<RoundScore> counted = new ArrayList<>();
        List<RoundScore> discarded = new ArrayList<>();
        for (RoundScore round : rounds) {
            (best.contains(round) ? counted : discarded).add(round);
        }
        return new ChallengeScore(counted, discarded, describe());
    }

    @Override
    public String describe() {
        return "mejores " + considered + " de " + outOf + " rondas";
    }
}
