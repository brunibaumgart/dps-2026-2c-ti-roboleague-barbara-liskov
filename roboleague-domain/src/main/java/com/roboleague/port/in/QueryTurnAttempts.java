package com.roboleague.port.in;

import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import java.util.Collection;
import java.util.Map;

/**
 * Shows a turn to whoever captures: its attempts in the order the domain opened them, the rulebook its category
 * plays with and whether it takes another result (never once the turn was cancelled), so the capture form never picks an attempt number, a rulebook or
 * a limit of its own. Reads only.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface QueryTurnAttempts {
    /**
     * The turn of one slot, or NotFoundException("Slot", slotId). Reads: the round by slot (1), then the three of
     * {@link #ofLoadedRounds} for its category, so 4 in all.
     */
    TurnResults execute(SlotId slotId);

    /**
     * Every slot of those rounds as the capture sees it, keyed by slot id. Reads: the rounds (1), their challenges
     * (1) and, per distinct (challenge, category), the category's rounds (1) and attempts (1); for one category, 4
     * reads however many rounds or slots are asked. A round that does not exist, or whose challenge does not, is an
     * InconsistentStateException: the ids come from slots already loaded.
     */
    Map<SlotId, TurnResults> ofRounds(Collection<RoundId> roundIds);
}
