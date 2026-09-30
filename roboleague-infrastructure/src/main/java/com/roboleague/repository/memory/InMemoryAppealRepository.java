package com.roboleague.repository.memory;

import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.AppealRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAppealRepository implements AppealRepository {
    private final Map<String, Appeal> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Appeal appeal) {
        Objects.requireNonNull(appeal, "appeal cannot be null");
        storage.put(appeal.getAppealId(), appeal);
    }

    @Override
    public Optional<Appeal> findById(String appealId) {
        return Optional.ofNullable(storage.get(appealId));
    }

    @Override
    public List<Appeal> findByAttemptId(String attemptId) {
        return storage.values().stream()
                .filter(a -> a.getAttemptId().equals(attemptId))
                .toList();
    }

    @Override
    public List<Appeal> findPendingAppeals() {
        return storage.values().stream()
                .filter(Appeal::isPending)
                .toList();
    }

    @Override
    public List<Appeal> findAll() {
        return new ArrayList<>(storage.values());
    }
}
