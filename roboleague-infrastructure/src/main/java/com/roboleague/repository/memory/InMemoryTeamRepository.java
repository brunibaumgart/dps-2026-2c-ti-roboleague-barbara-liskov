package com.roboleague.repository.memory;

import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTeamRepository implements TeamRepository {
    private final Map<String, Team> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Team team) {
        Objects.requireNonNull(team, "team cannot be null");
        storage.put(team.getId(), team);
    }

    @Override
    public Optional<Team> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Team> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<Team> findByCategory(String categoryId) {
        return storage.values().stream()
                .filter(t -> t.getCategory().id().equals(categoryId))
                .toList();
    }
}
