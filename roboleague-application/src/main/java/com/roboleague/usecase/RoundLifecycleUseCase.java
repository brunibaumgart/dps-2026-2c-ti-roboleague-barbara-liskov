package com.roboleague.usecase;

import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotCancellation;
import com.roboleague.scheduling.SlotId;
import com.roboleague.support.ActorId;
import com.roboleague.support.Clock;
import com.roboleague.support.ConflictException;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Moves a round and its turns through their lifecycle: the round starts, each turn starts and completes, and the
 * round completes once every turn completed or was cancelled. Cancelling a turn that has not started (a team that
 * did not show up) records who did it and why, frees its resources and stops the standings from waiting for it.
 * The lifecycle does not follow the results: capturing a result neither starts nor completes a turn.
 */
public class RoundLifecycleUseCase {
    private final RoundRepository rounds;
    private final AttemptRepository attempts;
    private final Clock clock;

    public RoundLifecycleUseCase(RoundRepository rounds, AttemptRepository attempts, Clock clock) {
        this.rounds = Objects.requireNonNull(rounds, "rounds cannot be null");
        this.attempts = Objects.requireNonNull(attempts, "attempts cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public Round start(RoundId roundId) {
        return saved(round(roundId).start());
    }

    public Round complete(RoundId roundId) {
        return saved(round(roundId).complete());
    }

    public Round startSlot(RoundId roundId, SlotId slotId) {
        return changeSlot(roundId, slotId, round -> round.startSlot(slotId));
    }

    public Round completeSlot(RoundId roundId, SlotId slotId) {
        return changeSlot(roundId, slotId, round -> round.completeSlot(slotId));
    }

    /**
     * Cancels a turn that has no attempt yet; a turn with results is corrected by disqualifying its attempt.
     */
    public Round cancelSlot(RoundId roundId, SlotId slotId, ActorId actorId, String reason) {
        return changeSlot(roundId, slotId, round -> {
            if (attempts.findByRoundId(roundId).stream().anyMatch(attempt -> attempt.getSlotId().equals(slotId))) {
                throw new ConflictException("Slot " + slotId + " already has results; disqualify its attempt instead");
            }
            return round.cancelSlot(slotId, new SlotCancellation(actorId, reason, clock.now()));
        });
    }

    private Round changeSlot(RoundId roundId, SlotId slotId, UnaryOperator<Round> transition) {
        Objects.requireNonNull(slotId, "slotId cannot be null");
        Round round = round(roundId);
        if (round.slot(slotId).isEmpty()) {
            throw new NotFoundException("Slot", slotId, "Slot " + slotId + " not found in round " + roundId);
        }
        return saved(transition.apply(round));
    }

    private Round round(RoundId roundId) {
        return rounds.findById(roundId).orElseThrow(() -> new NotFoundException("Round", roundId));
    }

    private Round saved(Round round) {
        rounds.save(round);
        return round;
    }
}
