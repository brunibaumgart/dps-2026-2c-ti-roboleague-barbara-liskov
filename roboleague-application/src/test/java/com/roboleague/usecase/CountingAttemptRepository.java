package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.scheduling.RoundId;
import com.roboleague.tournament.TeamId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** An AttemptRepository that counts its reads in a {@link ReadCounts} and delegates everything. */
final class CountingAttemptRepository implements AttemptRepository {
    private final AttemptRepository delegate;
    private final ReadCounts reads;

    CountingAttemptRepository(AttemptRepository delegate, ReadCounts reads) {
        this.delegate = delegate;
        this.reads = reads;
    }

    @Override
    public void save(Attempt attempt) {
        delegate.save(attempt);
    }

    @Override
    public Optional<Attempt> findById(AttemptId attemptId) {
        reads.count("findById");
        return delegate.findById(attemptId);
    }

    @Override
    public List<Attempt> findByIds(Collection<AttemptId> ids) {
        reads.count("findByIds");
        return delegate.findByIds(ids);
    }

    @Override
    public List<Attempt> findByTeamId(TeamId teamId) {
        reads.count("findByTeamId");
        return delegate.findByTeamId(teamId);
    }

    @Override
    public List<Attempt> findByRoundId(RoundId roundId) {
        reads.count("findByRoundId");
        return delegate.findByRoundId(roundId);
    }

    @Override
    public List<Attempt> findByRoundIds(Collection<RoundId> roundIds) {
        reads.count("findByRoundIds");
        return delegate.findByRoundIds(roundIds);
    }

    @Override
    public List<Attempt> findAll() {
        reads.count("findAll");
        return delegate.findAll();
    }
}
