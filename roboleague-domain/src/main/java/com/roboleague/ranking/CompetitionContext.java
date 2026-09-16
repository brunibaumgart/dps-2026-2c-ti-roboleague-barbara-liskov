package com.roboleague.ranking;

import java.util.Objects;

/**
 * Value object specifying tournament category and edition context.
 */
public record CompetitionContext(String categoryId, String editionId) {
    public CompetitionContext {
        Objects.requireNonNull(categoryId, "categoryId cannot be null");
        Objects.requireNonNull(editionId, "editionId cannot be null");
    }

    public static CompetitionContext of(String categoryId, String editionId) {
        return new CompetitionContext(categoryId, editionId);
    }
}
