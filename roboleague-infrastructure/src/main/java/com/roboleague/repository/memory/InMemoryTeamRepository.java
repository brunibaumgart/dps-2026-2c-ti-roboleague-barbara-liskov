package com.roboleague.repository.memory;

import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Same queries and ordering (by TeamId) as JpaTeamRepository, without durability or concurrency checks. */
public class InMemoryTeamRepository implements TeamRepository {
    private final Map<TeamId, Team> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Team team) {
        Objects.requireNonNull(team, "team cannot be null");
        storage.put(team.getId(), team);
    }

    @Override
    public Optional<Team> findById(TeamId id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Team> findAll() {
        return storage.values().stream().sorted(Comparator.comparing(team -> team.getId().value())).toList();
    }
}
