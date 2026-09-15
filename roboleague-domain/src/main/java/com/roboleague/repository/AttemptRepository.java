package com.roboleague.repository;

import com.roboleague.evaluation.Attempt;

import java.util.List;
import java.util.Optional;

public interface AttemptRepository {
    void save(Attempt attempt);
    Optional<Attempt> findById(String attemptId);
    List<Attempt> findByTeamId(String teamId);
    List<Attempt> findByRoundId(String roundId);
    List<Attempt> findAll();
}
