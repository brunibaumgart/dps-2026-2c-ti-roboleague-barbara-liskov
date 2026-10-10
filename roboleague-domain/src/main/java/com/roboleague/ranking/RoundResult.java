package com.roboleague.ranking;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.scheduling.RoundId;

import java.util.Objects;

/**
 * One round of a team as a standings version saw it: the attempt that counted for that round and its score.
 */
public record RoundResult(RoundId roundId, AttemptId attemptId, double total) {
    public RoundResult {
        Objects.requireNonNull(roundId, "roundId cannot be null");
        Objects.requireNonNull(attemptId, "attemptId cannot be null");
        if (!Double.isFinite(total)) {
            throw new IllegalArgumentException("a round total must be a finite number: " + total);
        }
    }
}
