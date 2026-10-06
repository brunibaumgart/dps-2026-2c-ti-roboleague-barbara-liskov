package com.roboleague.evaluation.scheme;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * A team's result in a challenge after selecting its rounds: which rounds counted, which were discarded and why.
 */
public record ChallengeScore(List<RoundScore> considered, List<RoundScore> discarded, String selectionRule) {
    public ChallengeScore {
        considered = List.copyOf(Objects.requireNonNull(considered, "considered cannot be null"));
        discarded = List.copyOf(Objects.requireNonNull(discarded, "discarded cannot be null"));
        Objects.requireNonNull(selectionRule, "selectionRule cannot be null");
    }

    public double total() {
        double total = 0.0;
        for (RoundScore round : considered) {
            total += round.total();
        }
        return total;
    }

    public OptionalDouble bestTime() {
        return considered.stream().mapToDouble(RoundScore::timeTakenSeconds).min();
    }

    /**
     * Points the deductions took away in the considered rounds; nothing without rounds.
     */
    public OptionalDouble deducted() {
        return considered.stream().mapToDouble(RoundScore::deducted).reduce(Double::sum);
    }

    public OptionalDouble judgeScore() {
        return considered.stream()
                .map(RoundScore::judgeScore)
                .filter(OptionalDouble::isPresent)
                .mapToDouble(OptionalDouble::getAsDouble)
                .average();
    }
}
