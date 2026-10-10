package com.roboleague.ranking;

import java.util.Objects;

/**
 * One row of a standings version: the team, its place with the reason and the rounds that make up its total.
 */
public record StandingsEntry(Placement placement, TeamIdentity team, TeamRounds rounds) {
    public StandingsEntry {
        Objects.requireNonNull(placement, "placement cannot be null");
        Objects.requireNonNull(team, "team cannot be null");
        Objects.requireNonNull(rounds, "rounds cannot be null");
    }

    public int position() {
        return placement.position();
    }

    public double total() {
        return rounds.total();
    }
}
