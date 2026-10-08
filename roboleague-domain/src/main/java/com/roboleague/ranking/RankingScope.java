package com.roboleague.ranking;

import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;

import java.util.Objects;

/**
 * Value object specifying edition and category scope of a ranking board.
 */
public record RankingScope(RankingId rankingId, EditionId editionId, CategoryId categoryId) {
    public RankingScope {
        Objects.requireNonNull(rankingId, "rankingId cannot be null");
        Objects.requireNonNull(editionId, "editionId cannot be null");
        Objects.requireNonNull(categoryId, "categoryId cannot be null");
    }

    public static RankingScope of(RankingId rankingId, EditionId editionId, CategoryId categoryId) {
        return new RankingScope(rankingId, editionId, categoryId);
    }
}
