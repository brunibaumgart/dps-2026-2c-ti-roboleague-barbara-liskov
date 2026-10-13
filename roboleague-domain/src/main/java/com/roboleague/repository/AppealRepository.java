package com.roboleague.repository;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealId;

import java.util.List;
import java.util.Optional;

/**
 * Stores appeals. {@link #save} throws {@link ConcurrentModificationConflict} when another writer created an appeal
 * with the same id first (Postgres adapter). Updates carry no version: the last write wins in every adapter.
 */
public interface AppealRepository {
    void save(Appeal appeal);
    Optional<Appeal> findById(AppealId appealId);
    List<Appeal> findByAttemptId(AttemptId attemptId);
    List<Appeal> findPendingAppeals();
    List<Appeal> findAll();
}
