package com.roboleague.evaluation.audit;

import com.roboleague.scheduling.JudgeId;
import com.roboleague.tournament.TeamId;

import java.util.Objects;

/**
 * Value object binding a team with the officiating judge for a registered result.
 */
public record TeamJudgeBinding(TeamId teamId, JudgeId judgeId) {
    public TeamJudgeBinding {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
    }

    public static TeamJudgeBinding of(TeamId teamId, JudgeId judgeId) {
        return new TeamJudgeBinding(teamId, judgeId);
    }
}
