package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.ScoreBreakdown;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;

import java.util.Objects;

/**
 * Use case to capture raw attempt measurements, evaluate against the challenge's current rulebook,
 * and append the initial audited score snapshot. A turn is captured once: a second capture would replace the
 * first, so corrections go through a fault adjustment or an appeal instead.
 */
public class CaptureAttemptResultUseCase {
    private final AttemptRepository attemptRepository;
    private final ChallengeRepository challengeRepository;

    public CaptureAttemptResultUseCase(AttemptRepository attemptRepository, ChallengeRepository challengeRepository) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
    }

    public Attempt execute(CaptureAttemptResultCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Rulebook rulebook = challengeRepository.findById(command.challengeId())
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + command.challengeId()))
                .currentRulebook();
        if (attemptRepository.findById(command.attempt().id()).isPresent()) {
            throw new IllegalStateException("Attempt " + command.attempt().id()
                    + " was already captured; corrections go through a fault adjustment or an appeal");
        }
        ScoreBreakdown breakdown = rulebook.evaluate(command.metrics());

        Attempt attempt = Attempt.of(command.attempt());
        attempt.registerInitialResult(command.metrics(), breakdown, command.judgeId());

        attemptRepository.save(attempt);
        return attempt;
    }
}
