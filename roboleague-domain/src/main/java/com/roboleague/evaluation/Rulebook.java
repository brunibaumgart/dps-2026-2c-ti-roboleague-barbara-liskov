package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.ScoreRule;
import com.roboleague.evaluation.rules.ScoreRule.RuleEvaluation;
import com.roboleague.evaluation.scheme.RankingScheme;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Published, immutable rules of a challenge: how an attempt scores and how teams are ranked.
 * Changing either means publishing a new version.
 */
public final class Rulebook {
    private final RulebookVersion version;
    private final List<ScoreRule> rules;
    private final RankingScheme rankingScheme;

    public Rulebook(RulebookVersion version, List<ScoreRule> rules, RankingScheme rankingScheme) {
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules cannot be null"));
        if (this.rules.isEmpty()) {
            throw new IllegalArgumentException("a rulebook needs at least one rule");
        }
        this.rankingScheme = Objects.requireNonNull(rankingScheme, "rankingScheme cannot be null");
    }

    public RulebookVersion version() {
        return version;
    }

    public RankingScheme rankingScheme() {
        return rankingScheme;
    }

    /**
     * Sources an attempt needs before it can be scored with this rulebook (F3).
     */
    public Set<ResultSource> requiredSources() {
        Set<ResultSource> sources = EnumSet.noneOf(ResultSource.class);
        for (ScoreRule rule : rules) {
            sources.add(rule.source());
        }
        return Set.copyOf(sources);
    }

    public ScoreBreakdown evaluate(RawMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics cannot be null");
        RuleEvaluation evaluation = RuleEvaluation.combining(rules, metrics);
        return ScoreBreakdown.of(evaluation.items(), evaluation.notes());
    }

    /**
     * The breakdown split by source, so a mixed challenge explains each contribution separately (F3).
     */
    public List<SourceContribution> contributions(RawMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics cannot be null");
        List<SourceContribution> contributions = new ArrayList<>();
        for (ResultSource source : ResultSource.values()) {
            List<ScoreRule> fromSource = rules.stream().filter(rule -> rule.source() == source).toList();
            if (!fromSource.isEmpty()) {
                contributions.add(new SourceContribution(source, RuleEvaluation.combining(fromSource, metrics)));
            }
        }
        return List.copyOf(contributions);
    }
}
