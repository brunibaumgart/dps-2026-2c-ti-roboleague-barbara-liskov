package com.roboleague.evaluation;

import com.roboleague.evaluation.scheme.AttemptSelection;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.support.BusinessRuleViolation;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.TeamId;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The attempts of one turn, numbered by the domain in the order they were opened, never by whoever captures. A
 * result goes to the turn's last attempt while it still awaits a source; otherwise it opens the next attempt, as
 * long as the rulebook admits another one per turn. A turn therefore runs its attempts one after the other.
 */
public final class TurnAttempts {
    private final SlotId slotId;
    private final List<Attempt> attempts;

    private TurnAttempts(SlotId slotId, List<Attempt> attempts) {
        this.slotId = slotId;
        this.attempts = attempts;
    }

    /**
     * The stored attempts of a turn. They have to belong to it and be numbered from 1 without gaps.
     */
    public static TurnAttempts of(SlotId slotId, List<Attempt> attempts) {
        Objects.requireNonNull(slotId, "slotId cannot be null");
        List<Attempt> ordered = Objects.requireNonNull(attempts, "attempts cannot be null").stream()
                .sorted(Comparator.comparingInt(attempt -> attempt.getId().number()))
                .toList();
        for (int index = 0; index < ordered.size(); index++) {
            AttemptId id = ordered.get(index).getId();
            if (!id.slotId().equals(slotId)) {
                throw new IllegalArgumentException("attempt " + id + " does not belong to turn " + slotId);
            }
            if (id.number() != index + 1) {
                throw new IllegalStateException("the attempts of turn " + slotId + " skip number " + (index + 1));
            }
        }
        return new TurnAttempts(slotId, ordered);
    }

    /**
     * The attempt that takes the next result of the turn: its last one while it awaits a source, scored with its own
     * rulebook; otherwise a new attempt numbered after the last one, opened with the given rulebook. Refused when
     * the turn already ran every attempt that rulebook admits.
     */
    public Attempt receiving(RoundId roundId, TeamId teamId, ChallengeId challengeId, Rulebook rulebook) {
        Objects.requireNonNull(roundId, "roundId cannot be null");
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(challengeId, "challengeId cannot be null");
        if (awaitingSource()) {
            return attempts.getLast();
        }
        if (!acceptsResult(rulebook)) {
            AttemptSelection selection = rulebook.rankingScheme().attemptSelection();
            throw new BusinessRuleViolation("Turn " + slotId + " already ran " + attempts.size()
                    + " attempt(s); rulebook v" + rulebook.version().number() + " admits " + selection.describe());
        }
        return Attempt.of(AttemptIdentity.of(AttemptId.of(slotId, attempts.size() + 1), roundId, teamId),
                RulebookReference.of(challengeId, rulebook));
    }

    /**
     * Whether the turn takes another result: its last attempt still awaits a source, or the rulebook admits one
     * more attempt per turn.
     */
    public boolean acceptsResult(Rulebook rulebook) {
        Objects.requireNonNull(rulebook, "rulebook cannot be null");
        return awaitingSource() || rulebook.rankingScheme().attemptSelection().allowsAttempt(attempts.size() + 1);
    }

    private boolean awaitingSource() {
        return !attempts.isEmpty() && !attempts.getLast().hasOutcome();
    }

    public List<Attempt> attempts() {
        return attempts;
    }
}
