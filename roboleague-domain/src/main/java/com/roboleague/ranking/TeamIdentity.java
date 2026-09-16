package com.roboleague.ranking;

import java.util.Objects;

/**
 * Value object representing team identification for leaderboard displays.
 */
public record TeamIdentity(String teamId, String teamName) {
    public TeamIdentity {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(teamName, "teamName cannot be null");
    }

    public static TeamIdentity of(String teamId, String teamName) {
        return new TeamIdentity(teamId, teamName);
    }
}
