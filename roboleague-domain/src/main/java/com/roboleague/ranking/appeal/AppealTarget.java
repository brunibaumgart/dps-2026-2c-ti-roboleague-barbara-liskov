package com.roboleague.ranking.appeal;

import java.util.Objects;

/**
 * Value object specifying the identifiers of the appeal, target attempt and owning team.
 */
public record AppealTarget(String appealId, String attemptId, String teamId) {
    public AppealTarget {
        Objects.requireNonNull(appealId, "appealId cannot be null");
        Objects.requireNonNull(attemptId, "attemptId cannot be null");
        Objects.requireNonNull(teamId, "teamId cannot be null");
    }

    public static AppealTarget of(String appealId, String attemptId, String teamId) {
        return new AppealTarget(appealId, attemptId, teamId);
    }
}
