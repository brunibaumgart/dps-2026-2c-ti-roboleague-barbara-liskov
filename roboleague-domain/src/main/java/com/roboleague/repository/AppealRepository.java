package com.roboleague.repository;

import com.roboleague.ranking.appeal.Appeal;

import java.util.List;
import java.util.Optional;

public interface AppealRepository {
    void save(Appeal appeal);
    Optional<Appeal> findById(String appealId);
    List<Appeal> findByAttemptId(String attemptId);
    List<Appeal> findPendingAppeals();
    List<Appeal> findAll();
}
