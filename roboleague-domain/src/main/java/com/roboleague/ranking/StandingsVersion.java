package com.roboleague.ranking;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One calculation of the standings, kept as it was: a later correction adds another version and never edits it.
 */
public record StandingsVersion(int number, LocalDateTime calculatedAt, StandingsTable table) {
    public StandingsVersion {
        if (number < 1) {
            throw new IllegalArgumentException("standings versions start at 1: " + number);
        }
        Objects.requireNonNull(calculatedAt, "calculatedAt cannot be null");
        Objects.requireNonNull(table, "table cannot be null");
    }

    /**
     * Where a version stands: provisional until it is published, official while it is the last one published,
     * replaced once a later version is published.
     */
    public enum Status { PROVISIONAL, OFFICIAL, REPLACED }
}
