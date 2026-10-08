package com.roboleague.ranking;

import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;

import java.util.Objects;

/**
 * Value object specifying tournament category and edition context.
 */
public record CompetitionContext(CategoryId categoryId, EditionId editionId) {
    public CompetitionContext {
        Objects.requireNonNull(categoryId, "categoryId cannot be null");
        Objects.requireNonNull(editionId, "editionId cannot be null");
    }

    public static CompetitionContext of(CategoryId categoryId, EditionId editionId) {
        return new CompetitionContext(categoryId, editionId);
    }
}
