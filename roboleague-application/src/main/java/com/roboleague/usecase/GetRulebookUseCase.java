package com.roboleague.usecase;

import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.ChallengeId;

import java.util.Objects;

/** Reads a published historical version without republishing or changing it. */
public class GetRulebookUseCase {
    private final ChallengeRepository challenges;

    public GetRulebookUseCase(ChallengeRepository challenges) {
        this.challenges = Objects.requireNonNull(challenges, "challenges cannot be null");
    }

    public Rulebook execute(ChallengeId challengeId, RulebookVersion version) {
        var challenge = challenges.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        return challenge.rulebook(version)
                .orElseThrow(() -> new IllegalArgumentException("Rulebook " + version + " not found for challenge " + challengeId));
    }
}
