package com.roboleague.repository;

import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsId;

import java.util.Optional;

/**
 * Stores standings. {@link #save} throws {@link ConcurrentModificationConflict} when someone else saved the same
 * standings after they were loaded, or created them first; the Postgres adapter detects both, the in-memory one
 * keeps the last write.
 */
public interface StandingsRepository {
    void save(Standings standings);
    Optional<Standings> findById(StandingsId id);
}
