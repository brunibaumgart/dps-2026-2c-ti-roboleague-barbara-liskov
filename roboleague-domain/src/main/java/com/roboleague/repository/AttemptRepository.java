package com.roboleague.repository;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.scheduling.RoundId;
import com.roboleague.tournament.TeamId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Stores attempts. {@link #save} throws {@link ConcurrentModificationConflict} when someone else saved the same
 * attempt after it was loaded, or created the same turn's attempt first; the Postgres adapter detects both, the
 * in-memory one keeps the last write.
 */
public interface AttemptRepository {
    void save(Attempt attempt);
    Optional<Attempt> findById(AttemptId attemptId);
    /** The attempts with those ids, ordered by id; ids that do not exist are simply absent. */
    List<Attempt> findByIds(Collection<AttemptId> ids);
    List<Attempt> findByTeamId(TeamId teamId);
    List<Attempt> findByRoundId(RoundId roundId);
    /** The attempts of any of those rounds, ordered by attempt id; empty ids give an empty list. */
    List<Attempt> findByRoundIds(Collection<RoundId> roundIds);
    List<Attempt> findAll();
}
