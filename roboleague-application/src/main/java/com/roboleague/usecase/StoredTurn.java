package com.roboleague.usecase;

import com.roboleague.evaluation.TurnAttempts;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.SlotId;

/**
 * Loads the stored attempts of one turn. Attempts numbered with gaps (only possible with the contract before the
 * domain numbered them) are broken data that no request can fix, so they are an inconsistent state, not a conflict.
 */
final class StoredTurn {
    private StoredTurn() {
    }

    static TurnAttempts of(AttemptRepository attempts, Round round, SlotId slotId) {
        try {
            return TurnAttempts.of(slotId, attempts.findByRoundId(round.getId()).stream()
                    .filter(attempt -> attempt.getSlotId().equals(slotId))
                    .toList());
        } catch (IllegalStateException broken) {
            throw new InconsistentStateException(broken.getMessage());
        }
    }
}
