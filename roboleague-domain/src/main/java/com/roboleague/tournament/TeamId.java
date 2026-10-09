package com.roboleague.tournament;

import java.util.Objects;

/** Textual identity of a team; preserves the supplied external value. */
public record TeamId(String value) implements Comparable<TeamId> {
    public TeamId {
        Objects.requireNonNull(value, "teamId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("teamId cannot be blank");
        }
    }

    public static TeamId of(String value) {
        return new TeamId(value);
    }

    @Override
    public int compareTo(TeamId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
