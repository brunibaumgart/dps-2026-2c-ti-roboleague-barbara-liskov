package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreBreakdown;
import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;

import java.util.Objects;

/**
 * Use case to formally resolve an appeal (Accept or Reject).
 * When accepted, it updates the attempt's audit trail and automatically
 * triggers ranking recalculation.
 */
public class ResolveAppealUseCase {
    private final AppealRepository appealRepository;
    private final AttemptRepository attemptRepository;
    private final ChallengeRepository challengeRepository;
    private final RecalculateRankingUseCase recalculateRankingUseCase;

    public ResolveAppealUseCase(AppealRepository appealRepository,
                                AttemptRepository attemptRepository,
                                ChallengeRepository challengeRepository,
                                RecalculateRankingUseCase recalculateRankingUseCase) {
        this.appealRepository = Objects.requireNonNull(appealRepository, "appealRepository cannot be null");
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
        this.recalculateRankingUseCase = Objects.requireNonNull(recalculateRankingUseCase, "recalculateRankingUseCase cannot be null");
    }

    public Appeal acceptAppeal(String appealId, ChallengeId challengeId, String categoryId, String roundId,
                               String resolutionNotes, RawMetrics revisedMetrics, String reviewerId) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException("Appeal not found: " + appealId));

        Attempt attempt = attemptRepository.findById(AttemptId.parse(appeal.getAttemptId()))
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + appeal.getAttemptId()));

        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        ScoreBreakdown revisedBreakdown = challenge.currentRulebook().evaluate(revisedMetrics);

        // Transition appeal state to ACCEPTED
        appeal.accept(resolutionNotes, revisedMetrics, reviewerId);

        // Adjust attempt audit trail
        attempt.adjustAfterAppeal(appealId, revisedMetrics, revisedBreakdown, resolutionNotes, reviewerId);

        attemptRepository.save(attempt);
        appealRepository.save(appeal);

        // Automatically trigger ranking recalculation
        recalculateRankingUseCase.execute(challenge.getEditionId(), categoryId, roundId);

        return appeal;
    }

    public Appeal rejectAppeal(String appealId, String resolutionNotes, String reviewerId) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException("Appeal not found: " + appealId));

        Attempt attempt = attemptRepository.findById(AttemptId.parse(appeal.getAttemptId()))
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + appeal.getAttemptId()));

        appeal.reject(resolutionNotes, reviewerId);
        attempt.restoreAfterRejectedAppeal();

        attemptRepository.save(attempt);
        appealRepository.save(appeal);

        return appeal;
    }
}
