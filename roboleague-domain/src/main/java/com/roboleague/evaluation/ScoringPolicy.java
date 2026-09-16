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
        PolicyInfo info,
        CompositeScoreRule compositeRule
) {
    public ScoringPolicy {
        Objects.requireNonNull(info, "info cannot be null");
        Objects.requireNonNull(compositeRule, "compositeRule cannot be null");
    }

    public String policyId() {
        return info.policyId();
    }

    public String version() {
        return info.version();
    }

    public String name() {
        return info.name();
    }

    public ScoreBreakdown evaluate(RawMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics cannot be null");
        return compositeRule.evaluateBreakdown(metrics);
    }

    public static ScoringPolicy of(PolicyInfo info, CompositeScoreRule compositeRule) {
        return new ScoringPolicy(info, compositeRule);
    }

    public static ScoringPolicy of(String policyId, String version, String name, List<ScoreRule> rules) {
        CompositeScoreRule composite = new CompositeScoreRule("Composite Policy " + name, rules);
        return new ScoringPolicy(new PolicyInfo(policyId, version, name), composite);
    }
}
