package com.roboleague.port.in;

import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Input port of QueryRounds.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface QueryRounds {
    Round get(RoundId id);

    /** The round that contains the slot, or NotFoundException("Slot", slotId). One read. */
    Round bySlot(SlotId slotId);

    /**
     * The rounds with those ids, keyed by id, in one read however many ids there are (none for an empty collection).
     * A stored reference that does not exist is an InconsistentStateException: the caller got the ids from attempts
     * or standings.
     */
    Map<RoundId, Round> byIds(Collection<RoundId> ids);

    List<Round> list(ChallengeId id, Optional<CategoryId> category);
}
