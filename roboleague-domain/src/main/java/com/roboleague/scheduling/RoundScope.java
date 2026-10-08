package com.roboleague.scheduling;

import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;

import java.util.Objects;

/**
 * Value object representing edition, category and sequence number of a round.
 */
public record RoundScope(EditionId editionId, CategoryId categoryId, int roundNumber) {
    public RoundScope {
        Objects.requireNonNull(editionId, "editionId cannot be null");
        Objects.requireNonNull(categoryId, "categoryId cannot be null");
        if (roundNumber <= 0) {
            throw new IllegalArgumentException("roundNumber must be positive");
        }
    }

    public static RoundScope of(EditionId editionId, CategoryId categoryId, int roundNumber) {
        return new RoundScope(editionId, categoryId, roundNumber);
    }
}
