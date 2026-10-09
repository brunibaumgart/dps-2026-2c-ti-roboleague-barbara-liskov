package com.roboleague.scheduling;

import java.util.Objects;

/** Textual identity of a track; preserves the supplied external value. */
public record TrackId(String value) {
    public TrackId {
        Objects.requireNonNull(value, "trackId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("trackId cannot be blank");
        }
    }

    public static TrackId of(String value) {
        return new TrackId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
