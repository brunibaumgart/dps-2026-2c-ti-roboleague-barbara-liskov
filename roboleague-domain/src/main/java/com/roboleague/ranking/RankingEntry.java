package com.roboleague.ranking;

import java.util.Objects;

/**
 * Entry in a tournament ranking table.
 */
public record RankingEntry(
        int position,
        TeamScore teamScore,
        TieStatus tieStatus
) {
    public RankingEntry {
        Objects.requireNonNull(teamScore, "teamScore cannot be null");
        Objects.requireNonNull(tieStatus, "tieStatus cannot be null");
        if (position <= 0) {
            throw new IllegalArgumentException("position must be >= 1");
        }
    }

    public boolean tiedWithPrevious() {
        return tieStatus.isTiedWithPrevious();
    }

    public String tieBreakerExplanation() {
        return tieStatus.tieBreakerExplanation();
    }

    public static RankingEntry of(int position, TeamScore teamScore, TieStatus tieStatus) {
        return new RankingEntry(position, teamScore, tieStatus);
    }

    public static RankingEntry of(int position, TeamScore teamScore, boolean tiedWithPrevious, String explanation) {
        return new RankingEntry(position, teamScore, new TieStatus(tiedWithPrevious, explanation));
    }
}
