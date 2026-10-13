package com.roboleague.tournament;

import com.roboleague.support.Identifiers;

/** Textual identity of a participant; preserves the supplied external value. */
public record ParticipantId(String value) {
    public ParticipantId {
        Identifiers.validate(value, "participantId");
    }

    public static ParticipantId of(String value) {
        return new ParticipantId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
