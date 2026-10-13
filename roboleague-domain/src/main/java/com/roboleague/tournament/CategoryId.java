package com.roboleague.tournament;

import com.roboleague.support.Identifiers;

/** Textual identity of a category; preserves the supplied external value. */
public record CategoryId(String value) {
    public CategoryId {
        Identifiers.validate(value, "category id");
    }

    public static CategoryId of(String value) {
        return new CategoryId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
