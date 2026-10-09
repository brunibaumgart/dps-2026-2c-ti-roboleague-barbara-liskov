package com.roboleague.ranking;

import java.util.Objects;

/** Textual identity of a ranking; preserves the supplied external value. */
public record RankingId(String value) {
    public RankingId {
        Objects.requireNonNull(value, "rankingId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("rankingId cannot be blank");
        }
    }

    public static RankingId of(String value) {
        return new RankingId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
