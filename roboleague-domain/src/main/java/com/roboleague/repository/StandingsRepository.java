package com.roboleague.repository;

import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsId;

import java.util.Optional;

public interface StandingsRepository {
    void save(Standings standings);
    Optional<Standings> findById(StandingsId id);
}
