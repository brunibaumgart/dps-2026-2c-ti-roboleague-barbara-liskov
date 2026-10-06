package com.roboleague.evaluation;

import java.util.Map;
import java.util.Objects;

/**
 * What the judge panel sent for an attempt: each judge's score and the named measurements the judges count,
 * such as rescued victims.
 */
public record JudgeScores(Map<String, Double> byJudge, Map<String, Double> named) implements SourceReport {

    public JudgeScores {
        byJudge = Map.copyOf(Objects.requireNonNull(byJudge, "judge scores cannot be null"));
        named = Map.copyOf(Objects.requireNonNull(named, "named measurements cannot be null"));
        byJudge.forEach((judgeId, score) -> {
            if (!Double.isFinite(score) || score < 0) {
                throw new IllegalArgumentException("score of judge '" + judgeId
                        + "' must be a finite, non-negative number: " + score);
            }
        });
    }

    @Override
    public ResultSource source() {
        return ResultSource.JUDGE_PANEL;
    }

    @Override
    public RawMetrics addTo(RawMetrics metrics) {
        return metrics.withJudgePanel(byJudge, named);
    }
}
