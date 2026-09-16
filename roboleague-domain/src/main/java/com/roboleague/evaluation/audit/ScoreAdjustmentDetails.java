package com.roboleague.evaluation.audit;

import java.util.Objects;

/**
 * Value object specifying revision number, motivation and author for a score adjustment.
 */
public record ScoreAdjustmentDetails(int newRevisionNumber, String reason, String authorId) {
    public ScoreAdjustmentDetails {
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(authorId, "authorId cannot be null");
        if (newRevisionNumber <= 0) {
            throw new IllegalArgumentException("newRevisionNumber must be positive");
        }
    }

    public static ScoreAdjustmentDetails of(int newRevisionNumber, String reason, String authorId) {
        return new ScoreAdjustmentDetails(newRevisionNumber, reason, authorId);
    }
}
