package com.roboleague.usecase;

import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.TurnAttempts;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.Challenge;

import java.util.Objects;

/**
 * Shows a turn to whoever captures: its attempts in the order the domain opened them, the rulebook its category
 * plays with and whether it takes another result (never once the turn was cancelled), so the capture form never picks an attempt number, a rulebook or
 * a limit of its own. Reads only.
 */
public class QueryTurnAttemptsUseCase {
    private final RoundRepository roundRepository;
    private final AttemptRepository attemptRepository;
    private final ChallengeRepository challengeRepository;
    private final CategoryRulebook categoryRulebook;

    public QueryTurnAttemptsUseCase(RoundRepository roundRepository, AttemptRepository attemptRepository,
                                    ChallengeRepository challengeRepository) {
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository cannot be null");
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
        this.categoryRulebook = new CategoryRulebook(roundRepository, attemptRepository);
    }

    public TurnResults execute(SlotId slotId) {
        Objects.requireNonNull(slotId, "slotId cannot be null");
        Round round = roundRepository.findBySlotId(slotId)
                .orElseThrow(() -> new NotFoundException("Slot", slotId));
        Challenge challenge = challengeRepository.findById(round.getChallengeId())
                .orElseThrow(() -> new InconsistentStateException("Round " + round.getId()
                        + " belongs to challenge " + round.getChallengeId() + ", which does not exist"));
        boolean cancelled = round.slot(slotId).map(slot -> slot.getStatus() == Slot.SlotStatus.CANCELLED)
                .orElseThrow(() -> new InconsistentStateException(
                        "Round " + round.getId() + " was found by slot " + slotId + " but does not contain it"));
        TurnAttempts turn = StoredTurn.of(attemptRepository, round, slotId);
        Rulebook rulebook = categoryRulebook.inForce(challenge, round.getCategoryId());
        return new TurnResults(turn.attempts(), rulebook, !cancelled && turn.acceptsResult(rulebook));
    }
}
