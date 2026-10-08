package com.roboleague.usecase;

import com.roboleague.evaluation.AppealRevision;
import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.evaluation.audit.OperationAudit;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.scheduling.RoundId;
import com.roboleague.support.ActorId;
import com.roboleague.support.Clock;
import com.roboleague.support.IdGenerator;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;

import java.util.Objects;
import java.util.Optional;

/**
 * Use case to formally resolve an appeal (Accept or Reject).
 * When accepted, it updates the attempt's audit trail and automatically
 * triggers ranking recalculation.
 */
public class ResolveAppealUseCase {
    private final Clock clock;
    private final IdGenerator ids;

    private final AppealRepository appealRepository;
    private final AttemptRepository attemptRepository;
    private final ChallengeRepository challengeRepository;
    private final RecalculateRankingUseCase recalculateRankingUseCase;

    public ResolveAppealUseCase(AppealRepository appealRepository,
                                AttemptRepository attemptRepository,
                                ChallengeRepository challengeRepository,
                                RecalculateRankingUseCase recalculateRankingUseCase, Clock clock, IdGenerator ids) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.ids = Objects.requireNonNull(ids, "ids cannot be null");
        this.appealRepository = Objects.requireNonNull(appealRepository, "appealRepository cannot be null");
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
        this.recalculateRankingUseCase = Objects.requireNonNull(recalculateRankingUseCase, "recalculateRankingUseCase cannot be null");
    }

    /**
     * Accepts the appeal and rescores the attempt with its own rulebook version: the caller does not choose the rules.
     */
    public Appeal acceptAppeal(AppealId appealId, CategoryId categoryId, Optional<RoundId> roundId,
                               String resolutionNotes, RawMetrics revisedMetrics, ActorId reviewerId) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException("Appeal not found: " + appealId));

        Attempt attempt = attemptRepository.findById(appeal.getAttemptId())
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + appeal.getAttemptId()));

        RulebookReference scoredWith = attempt.getRulebookReference();
        Challenge challenge = challengeRepository.findById(scoredWith.challengeId())
                .orElseThrow(() -> new IllegalStateException("Challenge of attempt " + attempt.getId()
                        + " not found: " + scoredWith.challengeId()));
        Rulebook rulebook = challenge.rulebook(scoredWith.version())
                .orElseThrow(() -> new IllegalStateException("Rulebook " + scoredWith + " not found"));

        // Transition appeal state to ACCEPTED
        var audit = new OperationAudit(clock.now(), ids.nextId(), ids.nextId());
        appeal.accept(resolutionNotes, revisedMetrics, reviewerId, audit.timestamp());

        // Adjust attempt audit trail
        attempt.adjustAfterAppeal(new AppealRevision(appealId, revisedMetrics, new AuditNote(reviewerId, resolutionNotes)),
                rulebook, audit);

        attemptRepository.save(attempt);
        appealRepository.save(appeal);

        // Automatically trigger ranking recalculation
        recalculateRankingUseCase.execute(challenge.getEditionId(), categoryId, roundId);

        return appeal;
    }

    public Appeal rejectAppeal(AppealId appealId, String resolutionNotes, ActorId reviewerId) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException("Appeal not found: " + appealId));

        Attempt attempt = attemptRepository.findById(appeal.getAttemptId())
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + appeal.getAttemptId()));

        appeal.reject(resolutionNotes, reviewerId, clock.now());
        attempt.restoreAfterRejectedAppeal();

        attemptRepository.save(attempt);
        appealRepository.save(appeal);

        return appeal;
    }
}
