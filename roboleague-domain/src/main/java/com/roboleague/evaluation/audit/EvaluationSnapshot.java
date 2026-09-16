package com.roboleague.evaluation.audit;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreBreakdown;

import java.util.Objects;

/**
 * Value object bundling the metrics captured and the resulting explainable breakdown.
 */
public record EvaluationSnapshot(RawMetrics metrics, ScoreBreakdown breakdown) {
    public EvaluationSnapshot {
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(breakdown, "breakdown cannot be null");
    }

    public static EvaluationSnapshot of(RawMetrics metrics, ScoreBreakdown breakdown) {
        return new EvaluationSnapshot(metrics, breakdown);
    }
}
