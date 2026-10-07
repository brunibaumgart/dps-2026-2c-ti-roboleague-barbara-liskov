package com.roboleague.usecase;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.tournament.ChallengeId;

import java.util.Objects;

/**
 * What one source sent for the attempt of a turn in a challenge, and the judge who loaded it.
 */
public record ReceiveResultCommand(ChallengeId challengeId, AttemptId attemptId, SourceDelivery delivery) {
    public ReceiveResultCommand {
        Objects.requireNonNull(challengeId, "challengeId cannot be null");
        Objects.requireNonNull(attemptId, "attemptId cannot be null");
        Objects.requireNonNull(delivery, "delivery cannot be null");
    }
}
