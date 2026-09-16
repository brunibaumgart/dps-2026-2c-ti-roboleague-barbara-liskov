package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreBreakdown;
import com.roboleague.evaluation.ScoringPolicy;
import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.tournament.Edition;

import java.util.Objects;

/**
 * Use case to formally resolve an appeal (Accept or Reject).
 * When accepted, it updates the attempt's audit trail and automatically
 * triggers ranking recalculation.
 */
public class ResolveAppealUseCase {
    private final AppealRepository appealRepository;
    private final AttemptRepository attemptRepository;
    private final EditionRepository editionRepository;
    private final RecalculateRankingUseCase recalculateRankingUseCase;

    public ResolveAppealUseCase(AppealRepository appealRepository,
                                AttemptRepository attemptRepository,
                                EditionRepository editionRepository,
                                RecalculateRankingUseCase recalculateRankingUseCase) {
        this.appealRepository = Objects.requireNonNull(appealRepository, "appealRepository cannot be null");
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.editionRepository = Objects.requireNonNull(editionRepository, "editionRepository cannot be null");
        this.recalculateRankingUseCase = Objects.requireNonNull(recalculateRankingUseCase, "recalculateRankingUseCase cannot be null");
    }

    public Appeal acceptAppeal(String appealId, String editionId, String categoryId, String roundId,
                               String resolutionNotes, RawMetrics revisedMetrics, String reviewerId) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException("Appeal not found: " + appealId));

        Attempt attempt = attemptRepository.findById(appeal.getAttemptId())
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + appeal.getAttemptId()));

        Edition edition = editionRepository.findById(editionId)
                .orElseThrow(() -> new IllegalArgumentException("Edition not found: " + editionId));

        ScoringPolicy policy = edition.getScoringPolicy();
        ScoreBreakdown revisedBreakdown = policy.evaluate(revisedMetrics);

        // Transition appeal state to ACCEPTED
        appeal.accept(resolutionNotes, revisedMetrics, reviewerId);

        // Adjust attempt audit trail
        attempt.adjustAfterAppeal(appealId, revisedMetrics, revisedBreakdown, resolutionNotes, reviewerId);

        attemptRepository.save(attempt);
        appealRepository.save(appeal);

        // Automatically trigger ranking recalculation
        recalculateRankingUseCase.execute(editionId, categoryId, roundId);

        return appeal;
    }

    public Appeal rejectAppeal(String appealId, String resolutionNotes, String reviewerId) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException("Appeal not found: " + appealId));

        Attempt attempt = attemptRepository.findById(appeal.getAttemptId())
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + appeal.getAttemptId()));

        appeal.reject(resolutionNotes, reviewerId);
        attempt.restoreAfterRejectedAppeal();

        attemptRepository.save(attempt);
        appealRepository.save(appeal);

        return appeal;
    }
}
