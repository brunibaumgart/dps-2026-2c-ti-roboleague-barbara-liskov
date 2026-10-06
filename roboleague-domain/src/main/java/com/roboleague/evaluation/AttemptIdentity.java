package com.roboleague.evaluation;

import java.util.Objects;

/**
 * Which turn an attempt was run in and by whom: its id (slot and attempt number), the round of the slot and the team.
 */
public record AttemptIdentity(AttemptId id, String roundId, String teamId) {
    public AttemptIdentity {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(roundId, "roundId cannot be null");
        Objects.requireNonNull(teamId, "teamId cannot be null");
    }

    public static AttemptIdentity of(AttemptId id, String roundId, String teamId) {
        return new AttemptIdentity(id, roundId, teamId);
    }
}
