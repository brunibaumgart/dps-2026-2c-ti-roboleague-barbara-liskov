package com.roboleague.usecase;

import com.roboleague.port.in.InconsistentStateException;
import com.roboleague.port.in.NotFoundException;

import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public class GetChallengeUseCase {
    private final ChallengeRepository challengeRepository;

    public GetChallengeUseCase(ChallengeRepository challengeRepository) {
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
    }

    public Challenge execute(ChallengeId challengeId) {
        return challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Challenge", challengeId));
    }

    /**
     * The challenges with those ids, keyed by id, in one read however many ids there are (none for an empty
     * collection). A stored reference that does not exist is an InconsistentStateException.
     */
    public Map<ChallengeId, Challenge> byIds(Collection<ChallengeId> challengeIds) {
        return StoredReferences.byIds(challengeIds, challengeRepository::findByIds, Challenge::getId,
                "Stored challenge not found");
    }
}
