package com.roboleague.repository.memory;

import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<Team> findByCategory(CategoryId categoryId) {
        return storage.values().stream()
                .filter(t -> t.getCategory().id().equals(categoryId))
                .toList();
    }
}
