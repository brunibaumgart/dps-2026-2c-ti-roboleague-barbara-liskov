package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.evaluation.audit.OperationAudit;
import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsId;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.Slot;
import com.roboleague.support.BusinessRuleViolation;
import com.roboleague.support.Clock;
import com.roboleague.support.IdGenerator;

import java.util.Objects;

/**
 * Corrects a scored attempt outside an appeal: faults seen after the capture (a new revision scored with the
 * attempt's own rulebook) or a disqualification by a judge of its turn. Either way the attempt keeps its history
 * and the standings of its challenge and category are recalculated, so the correction shows up as a new version.
 */
public class CorrectAttemptUseCase {
    private final Clock clock;
    private final IdGenerator ids;

    private final AttemptRepository attemptRepository;
    private final ChallengeRepository challengeRepository;
    private final RoundRepository roundRepository;
    private final RecalculateStandingsUseCase recalculateStandings;

    public CorrectAttemptUseCase(AttemptRepository attemptRepository, ChallengeRepository challengeRepository,
                                 RoundRepository roundRepository, RecalculateStandingsUseCase recalculateStandings,
                                 Clock clock, IdGenerator ids) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.ids = Objects.requireNonNull(ids, "ids cannot be null");
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository cannot be null");
        this.recalculateStandings = Objects.requireNonNull(recalculateStandings, "recalculateStandings cannot be null");
    }

    /**
     * Charges {@code additionalPenalties} more faults to the attempt, as the note's author decided and why.
     */
    public AttemptCorrection adjustPenalties(AttemptId attemptId, int additionalPenalties, AuditNote note) {
        Objects.requireNonNull(note, "note cannot be null");
        Attempt attempt = attempt(attemptId);
        RulebookReference scoredWith = attempt.getRulebookReference();
        Rulebook rulebook = challengeRepository.findById(scoredWith.challengeId())
                .flatMap(challenge -> challenge.rulebook(scoredWith.version()))
                .orElseThrow(() -> new InconsistentStateException("Rulebook " + scoredWith + " not found"));

        attempt.applyPenaltyAdjustment(additionalPenalties, note, rulebook, audit());
        attemptRepository.save(attempt);
        return new AttemptCorrection(attempt, recalculated(attempt, roundOf(attempt)).latest());
    }

    /**
     * Disqualifies the attempt. The judge has to be assigned to its turn, like the one who captures its results.
     */
    public AttemptCorrection disqualify(AttemptId attemptId, JudgeId judgeId, String reason) {
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
        Attempt attempt = attempt(attemptId);
        Round round = roundOf(attempt);
        Slot slot = round.slot(attempt.getSlotId()).orElseThrow(() -> new InconsistentStateException(
                "Round " + round.getId() + " does not contain the slot of attempt " + attempt.getId()));
        if (!slot.isJudgedBy(judgeId)) {
            throw new BusinessRuleViolation("judge " + judgeId + " is not assigned to slot " + slot.getSlotId());
        }

        attempt.disqualify(AuditNote.of(judgeId.asActorId(), reason), audit());
        attemptRepository.save(attempt);
        return new AttemptCorrection(attempt, recalculated(attempt, round).latest());
    }

    private Attempt attempt(AttemptId attemptId) {
        return attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException("Attempt", attemptId));
    }

    private Round roundOf(Attempt attempt) {
        return roundRepository.findById(attempt.getRoundId())
                .orElseThrow(() -> new InconsistentStateException("Round of attempt " + attempt.getId()
                        + " not found: " + attempt.getRoundId()));
    }

    private Standings recalculated(Attempt attempt, Round round) {
        return recalculateStandings.execute(
                new StandingsId(attempt.getRulebookReference().challengeId(), round.getCategoryId()));
    }

    private OperationAudit audit() {
        return new OperationAudit(clock.now(), ids.nextId(), ids.nextId());
    }
}
