package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.CompositeScoreRule;
import com.roboleague.evaluation.rules.ScoreRule;

import java.util.List;
import java.util.Objects;

/**
 * Published, immutable scoring rules of a challenge. Changing the rules means publishing a new version.
 */
public final class Rulebook {
    private final RulebookVersion version;
    private final CompositeScoreRule rules;

    public Rulebook(RulebookVersion version, List<ScoreRule> rules) {
        this.version = Objects.requireNonNull(version, "version cannot be null");
        if (Objects.requireNonNull(rules, "rules cannot be null").isEmpty()) {
            throw new IllegalArgumentException("a rulebook needs at least one rule");
        }
        this.rules = new CompositeScoreRule("Rulebook " + version, rules);
    }

    public RulebookVersion version() {
        return version;
    }

    public ScoreBreakdown evaluate(RawMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics cannot be null");
        return rules.evaluateBreakdown(metrics);
    }
}
