package com.roboleague.repository.memory;

import com.roboleague.repository.EditionRepository;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Same queries and ordering (by EditionId) as JpaEditionRepository, without durability or concurrency checks. */
public class InMemoryEditionRepository implements EditionRepository {
    private final Map<EditionId, Edition> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Edition edition) {
        Objects.requireNonNull(edition, "edition cannot be null");
        storage.put(edition.getId(), edition);
    }

    @Override
    public Optional<Edition> findById(EditionId id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Edition> findByTeamId(TeamId teamId) {
        return findAll().stream().filter(e -> e.registration(teamId).isPresent()).toList();
    }

    @Override
    public List<Edition> findAll() {
        return storage.values().stream().sorted(Comparator.comparing(edition -> edition.getId().value())).toList();
    }
}
