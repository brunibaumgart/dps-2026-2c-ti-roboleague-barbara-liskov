package com.roboleague.scheduling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Value object grouping available tracks and judges for scheduling.
 */
public record RoundResources(List<Track> tracks, List<Judge> judges) {
    public RoundResources {
        Objects.requireNonNull(tracks, "tracks cannot be null");
        Objects.requireNonNull(judges, "judges cannot be null");
        if (tracks.isEmpty()) {
            throw new IllegalArgumentException("At least one track must be available");
        }
        if (judges.isEmpty()) {
            throw new IllegalArgumentException("At least one judge must be available");
        }
        tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
        judges = Collections.unmodifiableList(new ArrayList<>(judges));
    }

    public static RoundResources of(List<Track> tracks, List<Judge> judges) {
        return new RoundResources(tracks, judges);
    }
}
