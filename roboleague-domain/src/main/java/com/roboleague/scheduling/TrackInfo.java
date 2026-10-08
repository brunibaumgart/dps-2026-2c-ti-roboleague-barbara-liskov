package com.roboleague.scheduling;

import java.util.Objects;

/**
 * Value object representing track identification and characteristics.
 */
public record TrackInfo(TrackId id, String name, String surfaceType) {
    public TrackInfo {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        surfaceType = surfaceType != null ? surfaceType : "STANDARD";
    }

    public static TrackInfo of(TrackId id, String name, String surfaceType) {
        return new TrackInfo(id, name, surfaceType);
    }
}
