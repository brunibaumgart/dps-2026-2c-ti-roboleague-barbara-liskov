package com.roboleague.evaluation.scheme;

import com.roboleague.evaluation.Points;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    public Points total() {
        Points total = Points.ZERO;
        for (RoundScore round : considered) {
            total = total.plus(round.total());
        }
        return total;
    }

    public OptionalDouble bestTime() {
        return considered.stream().mapToDouble(RoundScore::timeTakenSeconds).min();
    }

    /**
     * Points the deductions took away in the considered rounds; nothing without rounds.
     */
    public Optional<Points> deducted() {
        return considered.stream().map(RoundScore::deducted).reduce(Points::plus);
    }

    /**
     * The average of the judges' averages in the considered rounds, added up in decimal so that the order of the
     * rounds does not change it.
     */
    public OptionalDouble judgeScore() {
        List<BigDecimal> scores = considered.stream()
                .map(RoundScore::judgeScore)
                .filter(OptionalDouble::isPresent)
                .map(score -> BigDecimal.valueOf(score.getAsDouble()))
                .toList();
        if (scores.isEmpty()) {
            return OptionalDouble.empty();
        }
        BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return OptionalDouble.of(sum.divide(BigDecimal.valueOf(scores.size()), MathContext.DECIMAL64).doubleValue());
    }
}
