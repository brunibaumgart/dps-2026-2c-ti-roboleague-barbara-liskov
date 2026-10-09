package com.roboleague.ranking.appeal;

import java.util.Objects;

/** Textual identity of a appeal; preserves the supplied external value. */
public record AppealId(String value) {
    public AppealId {
        Objects.requireNonNull(value, "appealId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("appealId cannot be blank");
        }
    }

    public static AppealId of(String value) {
        return new AppealId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
