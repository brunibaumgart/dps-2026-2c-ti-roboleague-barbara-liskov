package com.roboleague.tournament;

import java.util.Objects;

/**
 * Tournament or competition entity.
 */
public record Tournament(
        String id,
        String name,
        String description,
        Season season
) {
    public Tournament {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(season, "season cannot be null");
    }
}
