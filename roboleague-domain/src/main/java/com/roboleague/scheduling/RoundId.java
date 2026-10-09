package com.roboleague.scheduling;

import java.util.Objects;

/** Textual identity of a round; preserves the supplied external value. */
public record RoundId(String value) {
    public RoundId {
        Objects.requireNonNull(value, "roundId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("roundId cannot be blank");
        }
    }

    public static RoundId of(String value) {
        return new RoundId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
