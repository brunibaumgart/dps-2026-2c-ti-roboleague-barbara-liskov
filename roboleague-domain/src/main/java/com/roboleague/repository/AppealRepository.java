package com.roboleague.repository;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealId;

import java.util.List;
import java.util.Optional;

public interface AppealRepository {
    void save(Appeal appeal);
    Optional<Appeal> findById(AppealId appealId);
    List<Appeal> findByAttemptId(AttemptId attemptId);
    List<Appeal> findPendingAppeals();
    List<Appeal> findAll();
}
