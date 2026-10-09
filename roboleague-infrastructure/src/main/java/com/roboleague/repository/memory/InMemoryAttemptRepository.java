package com.roboleague.repository.memory;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.scheduling.RoundId;
import com.roboleague.tournament.TeamId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAttemptRepository implements AttemptRepository {
    private final Map<AttemptId, Attempt> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Attempt attempt) {
        Objects.requireNonNull(attempt, "attempt cannot be null");
        storage.put(attempt.getId(), attempt);
    }

    @Override
    public Optional<Attempt> findById(AttemptId attemptId) {
        return Optional.ofNullable(storage.get(attemptId));
    }

    @Override
    public List<Attempt> findByTeamId(TeamId teamId) {
        return storage.values().stream()
                .filter(a -> a.getTeamId().equals(teamId))
                .toList();
    }

    @Override
    public List<Attempt> findByRoundId(RoundId roundId) {
        return storage.values().stream()
                .filter(a -> a.getRoundId().equals(roundId))
                .toList();
    }

    @Override
    public List<Attempt> findAll() {
        return new ArrayList<>(storage.values());
    }
}
