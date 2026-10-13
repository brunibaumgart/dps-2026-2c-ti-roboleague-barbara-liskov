package com.roboleague.scheduling;

import com.roboleague.support.Identifiers;

/** Textual identity of a track; preserves the supplied external value. */
public record TrackId(String value) {
    public TrackId {
        Identifiers.validate(value, "trackId");
    }

    public static TrackId of(String value) {
        return new TrackId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
