package com.roboleague.scheduling;

import java.util.List;
import java.util.Objects;

/**
 * Value object grouping available tracks and judges for scheduling.
 */
public record RoundResources(List<Track> tracks, List<Judge> judges) {
    public RoundResources {
        tracks = List.copyOf(Objects.requireNonNull(tracks, "tracks cannot be null"));
        judges = List.copyOf(Objects.requireNonNull(judges, "judges cannot be null"));
        if (tracks.stream().map(Track::id).distinct().count() != tracks.size()
                || judges.stream().map(Judge::id).distinct().count() != judges.size()) {
            throw new IllegalArgumentException("Resource ids must be unique");
        }
        tracks = tracks.stream().filter(Track::isActive).toList();
        if (tracks.isEmpty() || judges.isEmpty()) {
            throw new IllegalArgumentException("At least one active track and one judge must be available");
        }
    }

    public static RoundResources of(List<Track> tracks, List<Judge> judges) {
        return new RoundResources(tracks, judges);
    }
}
