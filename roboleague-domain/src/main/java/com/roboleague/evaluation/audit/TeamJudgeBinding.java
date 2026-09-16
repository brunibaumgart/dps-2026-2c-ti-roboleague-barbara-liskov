package com.roboleague.evaluation.audit;

import java.util.Objects;

/**
 * Value object binding a team with the officiating judge for a registered result.
 */
public record TeamJudgeBinding(String teamId, String judgeId) {
    public TeamJudgeBinding {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
    }

    public static TeamJudgeBinding of(String teamId, String judgeId) {
        return new TeamJudgeBinding(teamId, judgeId);
    }
}
