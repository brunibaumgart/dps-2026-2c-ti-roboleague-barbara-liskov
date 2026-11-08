package com.roboleague.usecase;

import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** A TeamRepository that counts its reads in a {@link ReadCounts} and delegates everything. */
final class CountingTeamRepository implements TeamRepository {
    private final TeamRepository delegate;
    private final ReadCounts reads;

    CountingTeamRepository(TeamRepository delegate, ReadCounts reads) {
        this.delegate = delegate;
        this.reads = reads;
    }

    @Override
    public void save(Team team) {
        delegate.save(team);
    }

    @Override
    public Optional<Team> findById(TeamId id) {
        reads.count("findById");
        return delegate.findById(id);
    }

    @Override
    public List<Team> findByIds(Collection<TeamId> ids) {
        reads.count("findByIds");
        return delegate.findByIds(ids);
    }

    @Override
    public List<Team> findAll() {
        reads.count("findAll");
        return delegate.findAll();
    }
}
