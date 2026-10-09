package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;

import java.util.Objects;

/**
 * Explains an attempt with the rulebook version it is scored with: what is still pending and, once scored, how
 * each source contributed.
 */
public class GetAttemptBreakdownUseCase {
    private final AttemptRepository attemptRepository;
    private final ChallengeRepository challengeRepository;

    public GetAttemptBreakdownUseCase(AttemptRepository attemptRepository, ChallengeRepository challengeRepository) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
    }

    public AttemptBreakdown execute(AttemptId attemptId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + attemptId));
        RulebookReference scoredWith = attempt.getRulebookReference();
        Rulebook rulebook = challengeRepository.findById(scoredWith.challengeId())
                .flatMap(challenge -> challenge.rulebook(scoredWith.version()))
                .orElseThrow(() -> new IllegalStateException("Rulebook " + scoredWith + " not found"));
        return new AttemptBreakdown(attempt, attempt.awaitedSources(rulebook), attempt.contributionsBySource(rulebook));
    }
}
