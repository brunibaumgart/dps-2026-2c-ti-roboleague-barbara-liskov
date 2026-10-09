package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.MeasurementCheck;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.audit.OperationAudit;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.Slot;
import com.roboleague.support.Clock;
import com.roboleague.support.IdGenerator;
import com.roboleague.tournament.Challenge;

import java.util.List;
import java.util.Objects;

/**
 * Receives what one source sent for an attempt: the automatic measurements or the judge panel's scores (F3).
 * The turn has to exist and the judge has to be assigned to it; the team is the slot's. The first result opens
 * the attempt with the challenge's current rulebook, and every later one is scored with that same version.
 */
public class ReceiveResultUseCase {
    private final Clock clock;
    private final IdGenerator ids;

    private final AttemptRepository attemptRepository;
    private final RoundRepository roundRepository;
    private final ChallengeRepository challengeRepository;

    public ReceiveResultUseCase(AttemptRepository attemptRepository, RoundRepository roundRepository,
                                ChallengeRepository challengeRepository, Clock clock, IdGenerator ids) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.ids = Objects.requireNonNull(ids, "ids cannot be null");
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository cannot be null");
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
    }

    public Reception execute(ReceiveResultCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Challenge challenge = challengeRepository.findById(command.challengeId())
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + command.challengeId()));
        AttemptId attemptId = command.attemptId();
        Round round = roundRepository.findBySlotId(attemptId.slotId())
                .orElseThrow(() -> new IllegalArgumentException("Slot not found: " + attemptId.slotId()));
        if (!round.getChallengeId().equals(command.challengeId())) {
            throw new IllegalStateException("Round " + round.getId() + " belongs to challenge " + round.getChallengeId()
                    + ", not " + command.challengeId());
        }
        Slot slot = round.slot(attemptId.slotId()).orElseThrow();
        JudgeId judgeId = command.delivery().judgeId();
        if (!slot.isJudgedBy(judgeId)) {
            return new Reception.Rejected(List.of("judge " + judgeId + " is not assigned to slot " + slot.getSlotId()));
        }

        Attempt attempt = attemptRepository.findById(attemptId).orElseGet(() -> Attempt.of(
                new AttemptIdentity(attemptId, round.getId(), slot.getTeamId()),
                RulebookReference.of(challenge.getId(), challenge.currentRulebook())));
        RulebookReference scoredWith = attempt.getRulebookReference();
        if (!scoredWith.challengeId().equals(challenge.getId())) {
            throw new IllegalStateException("Attempt " + attemptId + " belongs to challenge " + scoredWith.challengeId()
                    + ", not " + challenge.getId());
        }
        Rulebook rulebook = challenge.rulebook(scoredWith.version())
                .orElseThrow(() -> new IllegalStateException("Rulebook " + scoredWith + " not found"));

        return switch (attempt.receive(command.delivery(), rulebook, new OperationAudit(clock.now(), ids.nextId(), ids.nextId()))) {
            case MeasurementCheck.Rejected rejected -> new Reception.Rejected(rejected.problems());
            case MeasurementCheck.Accepted accepted -> {
                attemptRepository.save(attempt);
                yield new Reception.Received(attempt);
            }
        };
    }
}
