package com.roboleague.usecase;

import com.roboleague.ranking.PublicationCheck;
import com.roboleague.ranking.Standings;

import java.util.Objects;
import java.util.Optional;

/**
 * The calculated standings, if any, and the competition as it is now: what is still open and whether the
 * results changed since the latest version.
 */
public record StandingsOverview(Optional<Standings> standings, PublicationCheck now) {
    public StandingsOverview {
        Objects.requireNonNull(standings, "standings cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
    }

    /**
     * Whether the results changed since the latest version was calculated, so it should be recalculated.
     */
    public boolean outdated() {
        return standings.map(calculated -> !calculated.latest().table().equals(now.current())).orElse(true);
    }
}
