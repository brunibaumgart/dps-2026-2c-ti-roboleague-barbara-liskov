package com.roboleague.tournament;

import java.util.Objects;

/** Textual identity of a edition; preserves the supplied external value. */
public record EditionId(String value) {
    public EditionId {
        Objects.requireNonNull(value, "edition id cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("edition id cannot be blank");
        }
    }

    public static EditionId of(String value) {
        return new EditionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
