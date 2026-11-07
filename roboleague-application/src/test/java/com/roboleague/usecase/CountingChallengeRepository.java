package com.roboleague.usecase;

import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** A ChallengeRepository that counts its reads in a {@link ReadCounts} and delegates everything. */
final class CountingChallengeRepository implements ChallengeRepository {
    private final ChallengeRepository delegate;
    private final ReadCounts reads;

    CountingChallengeRepository(ChallengeRepository delegate, ReadCounts reads) {
        this.delegate = delegate;
        this.reads = reads;
    }

    @Override
    public void save(Challenge challenge) {
        delegate.save(challenge);
    }

    @Override
    public Optional<Challenge> findById(ChallengeId id) {
        reads.count("findById");
        return delegate.findById(id);
    }

    @Override
    public List<Challenge> findByIds(Collection<ChallengeId> ids) {
        reads.count("findByIds");
        return delegate.findByIds(ids);
    }

    @Override
    public List<Challenge> findByEditionId(EditionId editionId) {
        reads.count("findByEditionId");
        return delegate.findByEditionId(editionId);
    }
}
