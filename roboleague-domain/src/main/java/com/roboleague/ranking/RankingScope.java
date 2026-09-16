package com.roboleague.ranking;

import java.util.Objects;

/**
 * Value object specifying edition and category scope of a ranking board.
 */
public record RankingScope(String rankingId, String editionId, String categoryId) {
    public RankingScope {
        Objects.requireNonNull(rankingId, "rankingId cannot be null");
        Objects.requireNonNull(editionId, "editionId cannot be null");
        Objects.requireNonNull(categoryId, "categoryId cannot be null");
    }

    public static RankingScope of(String rankingId, String editionId, String categoryId) {
        return new RankingScope(rankingId, editionId, categoryId);
    }
}
