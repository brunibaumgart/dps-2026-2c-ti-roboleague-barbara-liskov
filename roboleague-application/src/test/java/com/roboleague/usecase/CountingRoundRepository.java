package com.roboleague.usecase;

import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.RoundScope;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.ChallengeId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** A RoundRepository that counts its reads in a {@link ReadCounts} and delegates everything. */
final class CountingRoundRepository implements RoundRepository {
    private final RoundRepository delegate;
    private final ReadCounts reads;

    CountingRoundRepository(RoundRepository delegate, ReadCounts reads) {
        this.delegate = delegate;
        this.reads = reads;
    }

    @Override
    public void save(Round round) {
        delegate.save(round);
    }

    @Override
    public Optional<Round> findById(RoundId roundId) {
        reads.count("findById");
        return delegate.findById(roundId);
    }

    @Override
    public List<Round> findByIds(Collection<RoundId> ids) {
        reads.count("findByIds");
        return delegate.findByIds(ids);
    }

    @Override
    public Optional<Round> findByScope(RoundScope scope) {
        reads.count("findByScope");
        return delegate.findByScope(scope);
    }

    @Override
    public List<Round> findByChallengeId(ChallengeId challengeId) {
        reads.count("findByChallengeId");
        return delegate.findByChallengeId(challengeId);
    }

    @Override
    public List<Round> findAll() {
        reads.count("findAll");
        return delegate.findAll();
    }

    @Override
    public Optional<Round> findBySlotId(SlotId slotId) {
        reads.count("findBySlotId");
        return delegate.findBySlotId(slotId);
    }
}
