package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.ranking.StandingsVersion;

import java.util.Objects;

/**
 * A corrected attempt (faults added or disqualified) with the standings version its correction produced.
 */
public record AttemptCorrection(Attempt attempt, StandingsVersion standings) {
    public AttemptCorrection {
        Objects.requireNonNull(attempt, "attempt cannot be null");
        Objects.requireNonNull(standings, "standings cannot be null");
    }
}
