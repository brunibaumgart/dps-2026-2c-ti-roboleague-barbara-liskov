package com.roboleague.tournament;

import java.util.Objects;

/**
 * Value object representing descriptive metadata of a tournament.
 */
public record TournamentInfo(String id, String name, String description) {
    public TournamentInfo {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        description = description != null ? description : "";
    }

    public static TournamentInfo of(String id, String name, String description) {
        return new TournamentInfo(id, name, description);
    }
}
