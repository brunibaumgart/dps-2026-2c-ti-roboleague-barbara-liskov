package com.roboleague.ranking;

import java.util.Objects;

/**
 * Entry in a tournament ranking table.
 */
public record RankingEntry(
        int position,
        TeamScore teamScore,
        boolean tiedWithPrevious,
        String tieBreakerExplanation
) {
    public RankingEntry {
        Objects.requireNonNull(teamScore, "teamScore cannot be null");
        if (position <= 0) {
            throw new IllegalArgumentException("position must be >= 1");
        }
    }
}
