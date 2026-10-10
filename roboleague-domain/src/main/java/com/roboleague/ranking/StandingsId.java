package com.roboleague.ranking;

import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;

import java.util.Objects;

/**
 * Identity of a standings table: there is one per challenge and category, so the domain derives it from both.
 */
public record StandingsId(ChallengeId challengeId, CategoryId categoryId) {
    private static final char SEPARATOR = '/';

    public StandingsId {
        Objects.requireNonNull(challengeId, "challengeId cannot be null");
        Objects.requireNonNull(categoryId, "categoryId cannot be null");
    }

    public static StandingsId of(ChallengeId challengeId, CategoryId categoryId) {
        return new StandingsId(challengeId, categoryId);
    }

    public String value() {
        return challengeId.value() + SEPARATOR + categoryId.value();
    }

    @Override
    public String toString() {
        return value();
    }
}
