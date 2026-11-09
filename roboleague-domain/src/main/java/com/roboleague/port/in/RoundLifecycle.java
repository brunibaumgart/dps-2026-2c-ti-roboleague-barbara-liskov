package com.roboleague.port.in;

import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.support.ActorId;

/**
 * Moves a round and its turns through their lifecycle: the round starts, each turn starts and completes, and the
 * round completes once every turn completed or was cancelled. Cancelling a turn that has not started (a team that
 * did not show up) records who did it and why, frees its resources and stops the standings from waiting for it.
 * The lifecycle does not follow the results: capturing a result neither starts nor completes a turn.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface RoundLifecycle {
    Round start(RoundId roundId);

    Round complete(RoundId roundId);

    Round startSlot(RoundId roundId, SlotId slotId);

    Round completeSlot(RoundId roundId, SlotId slotId);

    /**
     * Cancels a turn that has no attempt yet; a turn with results is corrected by disqualifying its attempt.
     */
    Round cancelSlot(RoundId roundId, SlotId slotId, ActorId actorId, String reason);
}
