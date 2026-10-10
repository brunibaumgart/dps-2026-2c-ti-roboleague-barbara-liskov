package com.roboleague.repository.memory;

import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsId;
import com.roboleague.repository.StandingsRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStandingsRepository implements StandingsRepository {
    private final Map<StandingsId, Standings> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Standings standings) {
        Objects.requireNonNull(standings, "standings cannot be null");
        storage.put(standings.getId(), standings);
    }

    @Override
    public Optional<Standings> findById(StandingsId id) {
        return Optional.ofNullable(storage.get(id));
    }
}
