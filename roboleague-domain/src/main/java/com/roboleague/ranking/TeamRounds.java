package com.roboleague.ranking;

import java.util.List;
import java.util.Objects;

/**
 * Which rounds of a team counted for its place and which were discarded, with the rule of the rulebook that chose
 * them (F1). The total is what the considered rounds add up to.
 */
public record TeamRounds(List<RoundResult> considered, List<RoundResult> discarded, String selectionRule) {
    public TeamRounds {
        considered = List.copyOf(Objects.requireNonNull(considered, "considered cannot be null"));
        discarded = List.copyOf(Objects.requireNonNull(discarded, "discarded cannot be null"));
        Objects.requireNonNull(selectionRule, "selectionRule cannot be null");
    }

    public double total() {
        double total = 0.0;
        for (RoundResult round : considered) {
            total += round.total();
        }
        return total;
    }
}
