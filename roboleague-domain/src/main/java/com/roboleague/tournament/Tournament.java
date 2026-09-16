package com.roboleague.tournament;

import java.util.Objects;

/**
 * Tournament or competition entity.
 */
public record Tournament(
        TournamentInfo info,
        Season season
) {
    public Tournament {
        Objects.requireNonNull(info, "info cannot be null");
        Objects.requireNonNull(season, "season cannot be null");
    }

    public String id() {
        return info.id();
    }

    public String name() {
        return info.name();
    }

    public String description() {
        return info.description();
    }

    public static Tournament of(TournamentInfo info, Season season) {
        return new Tournament(info, season);
    }

    public static Tournament of(String id, String name, Season season) {
        return new Tournament(new TournamentInfo(id, name, ""), season);
    }

    public static Tournament of(String id, String name, String description, Season season) {
        return new Tournament(new TournamentInfo(id, name, description), season);
    }
}
