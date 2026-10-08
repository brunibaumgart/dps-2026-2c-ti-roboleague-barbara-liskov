package com.roboleague.ranking.appeal;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.tournament.TeamId;

import java.util.Objects;

/**
 * Value object specifying the identifiers of the appeal, target attempt and owning team.
 */
public record AppealTarget(AppealId appealId, AttemptId attemptId, TeamId teamId) {
    public AppealTarget {
        Objects.requireNonNull(appealId, "appealId cannot be null");
        Objects.requireNonNull(attemptId, "attemptId cannot be null");
        Objects.requireNonNull(teamId, "teamId cannot be null");
    }

    public static AppealTarget of(AppealId appealId, AttemptId attemptId, TeamId teamId) {
        return new AppealTarget(appealId, attemptId, teamId);
    }
}
