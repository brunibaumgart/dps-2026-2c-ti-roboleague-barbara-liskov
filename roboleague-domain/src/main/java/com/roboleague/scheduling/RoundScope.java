package com.roboleague.scheduling;

import java.util.Objects;

/**
 * Value object representing edition, category and sequence number of a round.
 */
public record RoundScope(String editionId, String categoryId, int roundNumber) {
    public RoundScope {
        Objects.requireNonNull(editionId, "editionId cannot be null");
        Objects.requireNonNull(categoryId, "categoryId cannot be null");
        if (roundNumber <= 0) {
            throw new IllegalArgumentException("roundNumber must be positive");
        }
    }

    public static RoundScope of(String editionId, String categoryId, int roundNumber) {
        return new RoundScope(editionId, categoryId, roundNumber);
    }
}
