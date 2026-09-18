package com.roboleague.usecase;

import com.roboleague.scheduling.Judge;
import com.roboleague.scheduling.Track;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Input command for scheduling a round with its resources and timing.
 */
public record ScheduleRoundCommand(
        String editionId,
        String categoryId,
        int roundNumber,
        String roundName,
        List<Track> tracks,
        List<Judge> judges,
        LocalDateTime startTime,
        Duration slotDuration,
        Duration interval
) {
    public ScheduleRoundCommand {
        Objects.requireNonNull(editionId, "editionId cannot be null");
        Objects.requireNonNull(categoryId, "categoryId cannot be null");
        Objects.requireNonNull(roundName, "roundName cannot be null");
        tracks = List.copyOf(Objects.requireNonNull(tracks, "tracks cannot be null"));
        judges = List.copyOf(Objects.requireNonNull(judges, "judges cannot be null"));
        Objects.requireNonNull(startTime, "startTime cannot be null");
        Objects.requireNonNull(slotDuration, "slotDuration cannot be null");
        Objects.requireNonNull(interval, "interval cannot be null");
    }

    public static ScheduleRoundCommand of(String editionId, String categoryId, int roundNumber, String roundName,
                                          List<Track> tracks, List<Judge> judges, LocalDateTime startTime,
                                          Duration slotDuration, Duration interval) {
        return new ScheduleRoundCommand(
                editionId,
                categoryId,
                roundNumber,
                roundName,
                tracks,
                judges,
                startTime,
                slotDuration,
                interval
        );
    }
}
