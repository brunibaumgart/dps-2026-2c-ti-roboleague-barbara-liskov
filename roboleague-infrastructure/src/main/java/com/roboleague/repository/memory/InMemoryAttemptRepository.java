package com.roboleague.repository.memory;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptProgress;
import com.roboleague.evaluation.audit.AuditTrail;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.scheduling.RoundId;
import com.roboleague.tournament.TeamId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryAttemptRepository implements AttemptRepository {
    private final Map<AttemptId, Attempt> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Attempt attempt) {
        Objects.requireNonNull(attempt, "attempt cannot be null");
        storage.put(attempt.getId(), copy(attempt));
    }

    @Override
    public Optional<Attempt> findById(AttemptId attemptId) {
        return Optional.ofNullable(storage.get(attemptId)).map(InMemoryAttemptRepository::copy);
    }

    @Override
    public List<Attempt> findByTeamId(TeamId teamId) {
        return storage.values().stream()
                .filter(a -> a.getTeamId().equals(teamId))
                .map(InMemoryAttemptRepository::copy)
                .toList();
    }

    @Override
    public List<Attempt> findByRoundId(RoundId roundId) {
        return storage.values().stream()
                .filter(a -> a.getRoundId().equals(roundId))
                .map(InMemoryAttemptRepository::copy)
                .toList();
    }

    @Override
    public List<Attempt> findAll() {
        return storage.values().stream().map(InMemoryAttemptRepository::copy).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Stores and hands out copies, like a database would: a use case that changes an attempt and forgets to save it
     * leaves the stored one as it was, so its test fails.
     */
    private static Attempt copy(Attempt attempt) {
        return Attempt.restore(attempt.getIdentity(), attempt.getRulebookReference(), new AttemptProgress(
                attempt.getStage(), attempt.getDeliveries(),
                new AuditTrail(attempt.getRevisionHistory(), attempt.getEventHistory())));
    }
}
