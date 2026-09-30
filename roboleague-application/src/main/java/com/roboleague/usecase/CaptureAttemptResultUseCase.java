package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.ScoreBreakdown;
import com.roboleague.evaluation.ScoringPolicy;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.tournament.Edition;

import java.util.Objects;

/**
 * Use case to capture raw attempt measurements, evaluate against the edition's immutable
 * ScoringPolicy, and append the initial audited score snapshot.
 */
public class CaptureAttemptResultUseCase {
    private final AttemptRepository attemptRepository;
    private final EditionRepository editionRepository;

    public CaptureAttemptResultUseCase(AttemptRepository attemptRepository, EditionRepository editionRepository) {
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.editionRepository = Objects.requireNonNull(editionRepository, "editionRepository cannot be null");
    }

    public Attempt execute(CaptureAttemptResultCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Edition edition = editionRepository.findById(command.editionId())
                .orElseThrow(() -> new IllegalArgumentException("Edition not found: " + command.editionId()));

        ScoringPolicy policy = edition.getScoringPolicy();
        ScoreBreakdown breakdown = policy.evaluate(command.metrics());

        Attempt attempt = Attempt.of(
                command.attemptId(),
                command.teamId(),
                command.slotId(),
                command.roundId(),
                command.attemptNumber()
        );
        attempt.registerInitialResult(command.metrics(), breakdown, command.judgeId());

        attemptRepository.save(attempt);
        return attempt;
    }
}
