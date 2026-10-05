package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.CompositeScoreRule;
import com.roboleague.evaluation.rules.ScoreRule;
import com.roboleague.evaluation.scheme.RankingScheme;

import java.util.List;
import java.util.Objects;

/**
 * Published, immutable rules of a challenge: how an attempt scores and how teams are ranked.
 * Changing either means publishing a new version.
 */
public final class Rulebook {
    private final RulebookVersion version;
    private final CompositeScoreRule rules;
    private final RankingScheme rankingScheme;

    public Rulebook(RulebookVersion version, List<ScoreRule> rules, RankingScheme rankingScheme) {
        this.version = Objects.requireNonNull(version, "version cannot be null");
        if (Objects.requireNonNull(rules, "rules cannot be null").isEmpty()) {
            throw new IllegalArgumentException("a rulebook needs at least one rule");
        }
        this.rules = new CompositeScoreRule("Rulebook " + version, rules);
        this.rankingScheme = Objects.requireNonNull(rankingScheme, "rankingScheme cannot be null");
    }

    public RulebookVersion version() {
        return version;
    }

    public RankingScheme rankingScheme() {
        return rankingScheme;
    }

    public ScoreBreakdown evaluate(RawMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics cannot be null");
        return rules.evaluateBreakdown(metrics);
    }
}
