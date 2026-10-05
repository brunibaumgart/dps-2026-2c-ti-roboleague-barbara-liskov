package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.ScoreRule;
import com.roboleague.evaluation.rules.ScoreRule.RuleEvaluation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * How a rulebook scores an attempt: its score rules, its bonus rules and the limit on the sum of bonuses (F2).
 * The bonus rules are ordinary score rules; the limit wraps their sum without changing any of them.
 */
public record ScoringScheme(List<ScoreRule> rules, List<ScoreRule> bonuses, BonusLimit bonusLimit) {
    public ScoringScheme {
        rules = List.copyOf(Objects.requireNonNull(rules, "rules cannot be null"));
        bonuses = List.copyOf(Objects.requireNonNull(bonuses, "bonuses cannot be null"));
        Objects.requireNonNull(bonusLimit, "bonusLimit cannot be null");
        if (rules.isEmpty() && bonuses.isEmpty()) {
            throw new IllegalArgumentException("a scoring scheme needs at least one rule");
        }
    }

    public static ScoringScheme withoutBonuses(List<ScoreRule> rules) {
        return new ScoringScheme(rules, List.of(), new Unlimited());
    }

    public RuleEvaluation evaluate(RawMetrics metrics) {
        RuleEvaluation bonusesObtained = RuleEvaluation.combining(bonuses, metrics);
        return RuleEvaluation.concat(List.of(
                RuleEvaluation.combining(rules, metrics),
                bonusesObtained,
                bonusLimit.limit(bonusesObtained.total())));
    }

    public Set<ResultSource> requiredSources() {
        Set<ResultSource> sources = EnumSet.noneOf(ResultSource.class);
        for (ScoreRule rule : allRules()) {
            sources.add(rule.source());
        }
        return Set.copyOf(sources);
    }

    /**
     * What the rules of each source contributed. The bonus cap applies to the whole score, so it belongs to no source.
     */
    public List<SourceContribution> contributions(RawMetrics metrics) {
        List<SourceContribution> contributions = new ArrayList<>();
        for (ResultSource source : ResultSource.values()) {
            List<ScoreRule> fromSource = allRules().stream().filter(rule -> rule.source() == source).toList();
            if (!fromSource.isEmpty()) {
                contributions.add(new SourceContribution(source, RuleEvaluation.combining(fromSource, metrics)));
            }
        }
        return List.copyOf(contributions);
    }

    private List<ScoreRule> allRules() {
        List<ScoreRule> all = new ArrayList<>(rules);
        all.addAll(bonuses);
        return all;
    }
}
