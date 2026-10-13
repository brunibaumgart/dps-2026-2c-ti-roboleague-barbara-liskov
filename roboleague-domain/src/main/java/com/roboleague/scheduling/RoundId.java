package com.roboleague.scheduling;

import com.roboleague.support.Identifiers;

/** Textual identity of a round; preserves the supplied external value. */
public record RoundId(String value) {
    public RoundId {
        Identifiers.validate(value, "roundId");
    }

    public static RoundId of(String value) {
        return new RoundId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
