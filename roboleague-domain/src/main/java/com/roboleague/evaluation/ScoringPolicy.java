package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.CompositeScoreRule;
import com.roboleague.evaluation.rules.ScoreRule;

import java.util.List;
import java.util.Objects;

/**
 * Immutable rulebook scoring policy versioned per edition.
 * Guarantees that historical recalculations always use the exact rulebook version.
 */
public record ScoringPolicy(
        String policyId,
        String version,
        String name,
        CompositeScoreRule compositeRule
) {
    public ScoringPolicy {
        Objects.requireNonNull(policyId, "policyId cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(compositeRule, "compositeRule cannot be null");
    }

    public static ScoringPolicy of(String policyId, String version, String name, List<ScoreRule> rules) {
        CompositeScoreRule composite = new CompositeScoreRule("Composite Policy " + name, rules);
        return new ScoringPolicy(policyId, version, name, composite);
    }

    public ScoreBreakdown evaluate(RawMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics cannot be null");
        return compositeRule.evaluateBreakdown(metrics);
    }
}
