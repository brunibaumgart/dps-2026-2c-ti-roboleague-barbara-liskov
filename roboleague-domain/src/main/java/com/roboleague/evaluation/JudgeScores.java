package com.roboleague.evaluation;

import java.util.Map;
import java.util.Objects;

/**
 * What the judge panel sent for an attempt: each judge's score and the named measurements the judges count,
 * such as rescued victims.
 */
public record JudgeScores(Map<String, Double> byJudge, Map<String, Double> named) implements SourceReport {

    public JudgeScores {
        Objects.requireNonNull(byJudge, "judge scores cannot be null");
        Objects.requireNonNull(named, "named measurements cannot be null");
        named.forEach((name, value) -> {
            if (value == null) {
                throw new IllegalArgumentException("measurement '" + name + "' has no value");
            }
        });
        byJudge.forEach((judgeId, score) -> {
            if (score == null || !Double.isFinite(score) || score < 0) {
                throw new IllegalArgumentException("score of judge '" + judgeId
                        + "' must be a finite, non-negative number: " + score);
            }
        });
        byJudge = Map.copyOf(byJudge);
        named = Map.copyOf(named);
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
