package com.roboleague.ranking;

import com.roboleague.evaluation.Points;

import java.util.List;
import java.util.Objects;

/**
 * Which rounds of a team counted for its place and which were discarded, with the rules of the rulebook that chose
 * them (F1): how its rounds are selected and which attempt of each turn counts. The total is what the considered
 * rounds add up to.
 */
public record TeamRounds(List<RoundResult> considered, List<RoundResult> discarded, String selectionRule,
                         String attemptRule) {
    public TeamRounds {
        considered = List.copyOf(Objects.requireNonNull(considered, "considered cannot be null"));
        discarded = List.copyOf(Objects.requireNonNull(discarded, "discarded cannot be null"));
        Objects.requireNonNull(selectionRule, "selectionRule cannot be null");
        Objects.requireNonNull(attemptRule, "attemptRule cannot be null");
    }

    public Points total() {
        Points total = Points.ZERO;
        for (RoundResult round : considered) {
            total = total.plus(round.total());
        }
        return total;
    }
}
