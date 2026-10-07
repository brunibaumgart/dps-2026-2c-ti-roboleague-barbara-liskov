package com.roboleague.evaluation.audit;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoreBreakdown;

import java.util.Objects;

/**
 * Value object bundling the metrics captured, the rulebook version that scored them and the resulting
 * explainable breakdown.
 */
public record EvaluationSnapshot(RulebookVersion version, RawMetrics metrics, ScoreBreakdown breakdown) {
    public EvaluationSnapshot {
        Objects.requireNonNull(version, "version cannot be null");
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(breakdown, "breakdown cannot be null");
    }

    /**
     * Scores the metrics with the rulebook and keeps which version did it.
     */
    public static EvaluationSnapshot scoring(RawMetrics metrics, Rulebook rulebook) {
        return new EvaluationSnapshot(rulebook.version(), metrics, rulebook.evaluate(metrics));
    }
}
