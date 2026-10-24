package com.roboleague.usecase;

import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.ChallengeId;

import java.util.Objects;

/**
 * What one source sent for a turn of a challenge, and the judge who loaded it. It names the turn and not the
 * attempt: the domain decides which attempt takes it and numbers new ones in the order they arrive.
 */
public record ReceiveResultCommand(ChallengeId challengeId, SlotId slotId, SourceDelivery delivery) {
    public ReceiveResultCommand {
        Objects.requireNonNull(challengeId, "challengeId cannot be null");
        Objects.requireNonNull(slotId, "slotId cannot be null");
        Objects.requireNonNull(delivery, "delivery cannot be null");
    }
}
