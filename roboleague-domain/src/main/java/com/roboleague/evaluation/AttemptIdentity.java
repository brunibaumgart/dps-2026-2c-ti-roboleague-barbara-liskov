package com.roboleague.evaluation;

import java.util.Objects;

public record AttemptIdentity(String attemptId, String teamId, int attemptNumber) {
    public AttemptIdentity {
        Objects.requireNonNull(attemptId, "attemptId cannot be null");
        Objects.requireNonNull(teamId, "teamId cannot be null");
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
    }

    public static AttemptIdentity of(String attemptId, String teamId, int attemptNumber) {
        return new AttemptIdentity(attemptId, teamId, attemptNumber);
    }
}
