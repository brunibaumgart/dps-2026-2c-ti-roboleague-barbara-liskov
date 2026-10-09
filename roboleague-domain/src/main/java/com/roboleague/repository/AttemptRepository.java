package com.roboleague.repository;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.scheduling.RoundId;
import com.roboleague.tournament.TeamId;

import java.util.List;
import java.util.Optional;

public interface AttemptRepository {
    void save(Attempt attempt);
    Optional<Attempt> findById(AttemptId attemptId);
    List<Attempt> findByTeamId(TeamId teamId);
    List<Attempt> findByRoundId(RoundId roundId);
    List<Attempt> findAll();
}
