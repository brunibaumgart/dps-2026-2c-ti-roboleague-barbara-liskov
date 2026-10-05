package com.roboleague.usecase;

import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;

import java.util.Objects;

public class GetChallengeUseCase {
    private final ChallengeRepository challengeRepository;

    public GetChallengeUseCase(ChallengeRepository challengeRepository) {
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
    }

    public Challenge execute(ChallengeId challengeId) {
        return challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
    }
}
