package com.roboleague.support;

/** Textual identity of a actor; preserves the supplied external value. */
public record ActorId(String value) {
    public ActorId {
        Identifiers.validate(value, "actorId");
    }

    public static ActorId of(String value) {
        return new ActorId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
