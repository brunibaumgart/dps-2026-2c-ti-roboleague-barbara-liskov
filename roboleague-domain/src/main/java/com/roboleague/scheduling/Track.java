package com.roboleague.scheduling;

import java.util.Objects;

/**
 * Track or arena where robot attempts take place.
 */
public record Track(
        TrackInfo info,
        boolean isActive
) {
    public Track {
        Objects.requireNonNull(info, "info cannot be null");
    }

    public String id() {
        return info.id();
    }

    public String name() {
        return info.name();
    }

    public String surfaceType() {
        return info.surfaceType();
    }

    public static Track of(TrackInfo info, boolean isActive) {
        return new Track(info, isActive);
    }

    public static Track active(String id, String name, String surfaceType) {
        return new Track(new TrackInfo(id, name, surfaceType), true);
    }
}
