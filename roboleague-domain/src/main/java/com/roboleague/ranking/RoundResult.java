package com.roboleague.ranking;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.Points;
import com.roboleague.scheduling.RoundId;

import java.util.List;
import java.util.Objects;

/**
 * One round of a team as a standings version saw it: the attempt that counted for that round, its score, and the
 * other attempts of the turn, which did not count.
 */
public record RoundResult(RoundId roundId, AttemptId attemptId, Points total, List<AttemptId> otherAttempts) {
    public RoundResult {
        Objects.requireNonNull(roundId, "roundId cannot be null");
        Objects.requireNonNull(attemptId, "attemptId cannot be null");
        Objects.requireNonNull(total, "total cannot be null");
        otherAttempts = List.copyOf(Objects.requireNonNull(otherAttempts, "otherAttempts cannot be null"));
        if (otherAttempts.contains(attemptId)) {
            throw new IllegalArgumentException("attempt " + attemptId + " cannot be both the one that counted and another");
        }
    }

    /**
     * A round whose turn ran a single attempt.
     */
    public static RoundResult single(RoundId roundId, AttemptId attemptId, Points total) {
        return new RoundResult(roundId, attemptId, total, List.of());
    }
}
