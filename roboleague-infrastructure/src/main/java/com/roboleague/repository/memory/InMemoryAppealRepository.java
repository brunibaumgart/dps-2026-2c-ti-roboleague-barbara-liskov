package com.roboleague.repository.memory;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealClaim;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.ranking.appeal.AppealTarget;
import com.roboleague.repository.AppealRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryAppealRepository implements AppealRepository {
    private final Map<AppealId, Appeal> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Appeal appeal) {
        Objects.requireNonNull(appeal, "appeal cannot be null");
        storage.put(appeal.getAppealId(), copy(appeal));
    }

    @Override
    public Optional<Appeal> findById(AppealId appealId) {
        return Optional.ofNullable(storage.get(appealId)).map(InMemoryAppealRepository::copy);
    }

    @Override
    public List<Appeal> findByAttemptId(AttemptId attemptId) {
        return storage.values().stream()
                .filter(a -> a.getAttemptId().equals(attemptId))
                .map(InMemoryAppealRepository::copy)
                .toList();
    }

    @Override
    public List<Appeal> findPendingAppeals() {
        return storage.values().stream()
                .filter(Appeal::isPending)
                .map(InMemoryAppealRepository::copy)
                .toList();
    }

    @Override
    public List<Appeal> findAll() {
        return storage.values().stream().map(InMemoryAppealRepository::copy).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Stores and hands out copies, like a database would: a use case that changes an appeal and forgets to save it
     * leaves the stored one as it was, so its test fails.
     */
    private static Appeal copy(Appeal appeal) {
        return Appeal.restore(AppealTarget.of(appeal.getAppealId(), appeal.getAttemptId(), appeal.getTeamId()),
                AppealClaim.of(appeal.getReason(), appeal.getEvidenceDescription()), appeal.progress());
    }
}
