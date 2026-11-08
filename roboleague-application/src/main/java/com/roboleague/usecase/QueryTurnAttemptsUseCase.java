package com.roboleague.usecase;

import com.roboleague.port.in.QueryTurnAttempts;
import com.roboleague.port.in.InconsistentStateException;
import com.roboleague.port.in.NotFoundException;
import com.roboleague.port.in.TurnResults;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.TurnAttempts;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shows a turn to whoever captures: its attempts in the order the domain opened them, the rulebook its category
 * plays with and whether it takes another result (never once the turn was cancelled), so the capture form never picks an attempt number, a rulebook or
 * a limit of its own. Reads only.
 */
public class QueryTurnAttemptsUseCase implements QueryTurnAttempts {
    private final RoundRepository roundRepository;
    private final ChallengeRepository challengeRepository;
    private final CategoryRulebook categoryRulebook;

    public QueryTurnAttemptsUseCase(RoundRepository roundRepository, AttemptRepository attemptRepository,
                                    ChallengeRepository challengeRepository) {
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository cannot be null");
        this.challengeRepository = Objects.requireNonNull(challengeRepository, "challengeRepository cannot be null");
        this.categoryRulebook = new CategoryRulebook(roundRepository, attemptRepository);
    }

    /**
     * The turn of one slot, or NotFoundException("Slot", slotId). Reads: the round by slot (1), then the three of
     * {@link #ofLoadedRounds} for its category, so 4 in all.
     */
    public TurnResults execute(SlotId slotId) {
        Objects.requireNonNull(slotId, "slotId cannot be null");
        Round round = roundRepository.findBySlotId(slotId)
                .orElseThrow(() -> new NotFoundException("Slot", slotId));
        round.slot(slotId).orElseThrow(() -> new InconsistentStateException(
                "Round " + round.getId() + " was found by slot " + slotId + " but does not contain it"));
        return ofLoadedRounds(List.of(round)).get(slotId);
    }

    /**
     * Every slot of those rounds as the capture sees it, keyed by slot id. Reads: the rounds (1), their challenges
     * (1) and, per distinct (challenge, category), the category's rounds (1) and attempts (1); for one category, 4
     * reads however many rounds or slots are asked. A round that does not exist, or whose challenge does not, is an
     * InconsistentStateException: the ids come from slots already loaded.
     */
    public Map<SlotId, TurnResults> ofRounds(Collection<RoundId> roundIds) {
        return ofLoadedRounds(StoredReferences.byIds(roundIds, roundRepository::findByIds, Round::getId,
                "Stored round not found").values());
    }

    /**
     * Reads: the challenges of the rounds (1) and, per distinct (challenge, category), the category's rounds (1)
     * and attempts (1).
     */
    private Map<SlotId, TurnResults> ofLoadedRounds(Collection<Round> rounds) {
        List<ChallengeId> challengeIds = rounds.stream().map(Round::getChallengeId).distinct().toList();
        Map<ChallengeId, Challenge> challenges = StoredReferences.byIds(challengeIds,
                challengeRepository::findByIds, Challenge::getId, "Challenge of a round not found");
        Map<SlotId, TurnResults> results = new LinkedHashMap<>();
        Map<CategoryKey, List<Round>> groups = new LinkedHashMap<>();
        for (Round round : rounds) {
            groups.computeIfAbsent(new CategoryKey(round.getChallengeId(), round.getCategoryId()),
                    key -> new ArrayList<>()).add(round);
        }
        groups.forEach((key, requested) -> {
            Challenge challenge = challenges.get(key.challengeId());
            List<Attempt> played = categoryRulebook.attemptsOf(categoryRulebook.roundsOf(challenge, key.categoryId()));
            Rulebook rulebook = CategoryRulebook.inForce(challenge, played);
            for (Round round : requested) {
                List<Attempt> ofRound = played.stream()
                        .filter(attempt -> attempt.getRoundId().equals(round.getId())).toList();
                for (Slot slot : round.getSlots()) {
                    TurnAttempts turn = StoredTurn.of(ofRound, slot.getSlotId());
                    boolean cancelled = slot.getStatus() == Slot.SlotStatus.CANCELLED;
                    results.put(slot.getSlotId(),
                            new TurnResults(turn.attempts(), rulebook, !cancelled && turn.acceptsResult(rulebook)));
                }
            }
        });
        return results;
    }

    private record CategoryKey(ChallengeId challengeId, CategoryId categoryId) {
    }
}
