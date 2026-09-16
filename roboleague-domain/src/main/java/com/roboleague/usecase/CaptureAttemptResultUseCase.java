package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.RawMetrics;
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

    public Attempt execute(String editionId, String attemptId, String teamId, String slotId, String roundId,
                           int attemptNumber, RawMetrics metrics, String judgeId) {
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Edition edition = editionRepository.findById(editionId)
                .orElseThrow(() -> new IllegalArgumentException("Edition not found: " + editionId));

        ScoringPolicy policy = edition.getScoringPolicy();
        ScoreBreakdown breakdown = policy.evaluate(metrics);

        Attempt attempt = Attempt.of(attemptId, teamId, slotId, roundId, attemptNumber);
        attempt.registerInitialResult(metrics, breakdown, judgeId);

        attemptRepository.save(attempt);
        return attempt;
    }
}
