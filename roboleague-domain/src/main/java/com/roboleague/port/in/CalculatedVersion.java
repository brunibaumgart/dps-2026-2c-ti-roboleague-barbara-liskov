package com.roboleague.port.in;

import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsVersion;

import java.util.Objects;

/**
 * One calculated version of the standings, next to the standings it belongs to (they know whether it is official).
 */
public record CalculatedVersion(Standings standings, StandingsVersion version) {
    public CalculatedVersion {
        Objects.requireNonNull(standings, "standings cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
    }
}
