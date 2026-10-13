package com.roboleague.ranking.appeal;

import com.roboleague.support.Identifiers;

/** Textual identity of a appeal; preserves the supplied external value. */
public record AppealId(String value) {
    public AppealId {
        Identifiers.validate(value, "appealId");
    }

    public static AppealId of(String value) {
        return new AppealId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
