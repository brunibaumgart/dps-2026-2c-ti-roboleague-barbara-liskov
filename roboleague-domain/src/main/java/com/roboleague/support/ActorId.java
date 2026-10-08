package com.roboleague.support;

import java.util.Objects;

/** Textual identity of a actor; preserves the supplied external value. */
public record ActorId(String value) {
    public ActorId {
        Objects.requireNonNull(value, "actorId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("actorId cannot be blank");
        }
    }

    public static ActorId of(String value) {
        return new ActorId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
