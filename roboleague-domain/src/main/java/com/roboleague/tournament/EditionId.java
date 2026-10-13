package com.roboleague.tournament;

import com.roboleague.support.Identifiers;

/** Textual identity of a edition; preserves the supplied external value. */
public record EditionId(String value) {
    public EditionId {
        Identifiers.validate(value, "edition id");
    }

    public static EditionId of(String value) {
        return new EditionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
