package com.roboleague.tournament;

import java.util.Objects;

public record ChallengeId(String value) {
    public ChallengeId {
        Objects.requireNonNull(value, "value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }

    public static ChallengeId of(String value) {
        return new ChallengeId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
