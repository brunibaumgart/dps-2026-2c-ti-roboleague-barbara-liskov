package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.MeasurementCheck;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.TurnAttempts;
import com.roboleague.evaluation.audit.OperationAudit;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.SlotId;
import com.roboleague.support.Clock;
import com.roboleague.support.ConflictException;
import com.roboleague.support.IdGenerator;
import com.roboleague.tournament.Challenge;

import java.util.List;
import java.util.Objects;

/**
 * Receives what one source sent for a turn: the automatic measurements or the judge panel's scores (F3).
 * The turn has to exist and the judge has to be assigned to it; the team is the slot's. The result goes to the
 * turn's attempt that still awaits a source, scored with its own version; otherwise it opens the next attempt,
 * numbered by the domain in the order it arrives, with the rulebook its category plays with, while that rulebook
 * admits another attempt per turn ({@link TurnAttempts}).
 */
public class ReceiveResultUseCase {
    private final Clock clock;
    private final IdGenerator ids;

    private final AttemptRepository attemptRepository;
    private final RoundRepository roundRepository;
    private final ChallengeRepository challengeRepository;
    private final CategoryRulebook categoryRulebook;

    public ReceiveResultUseCase(AttemptRepository attemptRepository, RoundRepository roundRepository,
                                ChallengeRepository challengeRepository, Clock clock, IdGenerator ids) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.ids = Objects.requireNonNull(ids, "ids cannot be null");
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository cannot be null");
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
        this.categoryRulebook = new CategoryRulebook(roundRepository, attemptRepository);
    }

    public Reception execute(ReceiveResultCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Challenge challenge = challengeRepository.findById(command.challengeId())
                .orElseThrow(() -> new NotFoundException("Challenge", command.challengeId()));
        SlotId slotId = command.slotId();
        Round round = roundRepository.findBySlotId(slotId)
                .orElseThrow(() -> new NotFoundException("Slot", slotId));
        if (!round.getChallengeId().equals(command.challengeId())) {
            throw new IllegalStateException("Round " + round.getId() + " belongs to challenge " + round.getChallengeId()
                    + ", not " + command.challengeId());
        }
        Slot slot = round.slot(slotId).orElseThrow(() -> new InconsistentStateException(
                "Round " + round.getId() + " was found by slot " + slotId + " but does not contain it"));
        if (slot.getStatus() == Slot.SlotStatus.CANCELLED) {
            throw new ConflictException("Slot " + slot.getSlotId() + " was cancelled; it takes no results");
        }
        JudgeId judgeId = command.delivery().judgeId();
        if (!slot.isJudgedBy(judgeId)) {
            return new Reception.Rejected(List.of("judge " + judgeId + " is not assigned to slot " + slot.getSlotId()));
        }

        TurnAttempts turn = StoredTurn.of(attemptRepository, round, slotId);
        Attempt attempt = turn.receiving(round.getId(), slot.getTeamId(), challenge.getId(),
                categoryRulebook.inForce(challenge, round.getCategoryId()));
        RulebookReference scoredWith = attempt.getRulebookReference();
        if (!scoredWith.challengeId().equals(challenge.getId())) {
            throw new InconsistentStateException("Attempt " + attempt.getId() + " belongs to challenge "
                    + scoredWith.challengeId() + ", not " + challenge.getId());
        }
        Rulebook rulebook = challenge.rulebook(scoredWith.version())
                .orElseThrow(() -> new InconsistentStateException("Rulebook " + scoredWith + " not found"));

        return switch (attempt.receive(command.delivery(), rulebook, new OperationAudit(clock.now(), ids.nextId(), ids.nextId()))) {
            case MeasurementCheck.Rejected rejected -> new Reception.Rejected(rejected.problems());
            case MeasurementCheck.Accepted accepted -> {
                attemptRepository.save(attempt);
                yield new Reception.Received(attempt);
            }
        };
    }
}
