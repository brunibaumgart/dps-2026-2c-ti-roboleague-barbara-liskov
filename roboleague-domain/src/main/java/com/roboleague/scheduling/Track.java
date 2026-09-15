package com.roboleague.scheduling;

import java.util.Objects;

/**
 * Track or arena where robot attempts take place.
 */
public record Track(
        String id,
        String name,
        String surfaceType,
        boolean isActive
) {
    public Track {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
    }

    public static Track active(String id, String name, String surfaceType) {
        return new Track(id, name, surfaceType, true);
    }
}
