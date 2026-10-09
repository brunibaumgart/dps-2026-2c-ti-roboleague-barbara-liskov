package com.roboleague.tournament;

import java.util.Objects;

/** Textual identity of a participant; preserves the supplied external value. */
public record ParticipantId(String value) {
    public ParticipantId {
        Objects.requireNonNull(value, "participantId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("participantId cannot be blank");
        }
    }

    public static ParticipantId of(String value) {
        return new ParticipantId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
