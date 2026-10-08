package com.roboleague.evaluation;

import com.roboleague.scheduling.RoundId;
import com.roboleague.tournament.TeamId;

import java.util.Objects;

/**
 * Which turn an attempt was run in and by whom: its id (slot and attempt number), the round of the slot and the team.
 */
public record AttemptIdentity(AttemptId id, RoundId roundId, TeamId teamId) {
    public AttemptIdentity {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(roundId, "roundId cannot be null");
        Objects.requireNonNull(teamId, "teamId cannot be null");
    }

    public static AttemptIdentity of(AttemptId id, RoundId roundId, TeamId teamId) {
        return new AttemptIdentity(id, roundId, teamId);
    }
}
