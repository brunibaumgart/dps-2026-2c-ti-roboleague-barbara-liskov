package com.roboleague.evaluation;

import com.roboleague.evaluation.definition.RuleDefinition;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.evaluation.rules.BaseRule;
import com.roboleague.evaluation.rules.DeductionRule;
import com.roboleague.evaluation.rules.ScoreRule;
import com.roboleague.evaluation.rules.ScoreRule.RuleEvaluation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * How a rulebook scores an attempt: the metrics it declares, its score and bonus rules, and the limit on the sum
 * of bonuses (F2). The bonus rules are ordinary score rules; the limit wraps their sum without changing any of them.
 * Every metric a rule reads has to be declared, so a misspelled name is rejected when publishing, not when scoring.
 */
public record ScoringScheme(MetricSheet metrics, ScoreRules scoreRules, BonusLimit bonusLimit) {
    public ScoringScheme {
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(scoreRules, "scoreRules cannot be null");
        Objects.requireNonNull(bonusLimit, "bonusLimit cannot be null");
        List<String> undeclared = new ArrayList<>();
        for (ScoreRule rule : scoreRules.all()) {
            for (Metric metric : rule.metrics()) {
                if (!metrics.declares(metric)) {
                    undeclared.add("rule '" + rule.getRuleName() + "' reads metric '" + metric.name() + "' ("
                            + metric.source() + "), which the rulebook does not declare");
                }
            }
        }
        if (!undeclared.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", undeclared));
        }
    }

    /**
     * A scheme with no bonuses for rules that read no named metric, so there is nothing to declare.
     */
    public static ScoringScheme withoutBonuses(List<BaseRule> base, List<DeductionRule> deductions) {
        return new ScoringScheme(MetricSheet.none(), new ScoreRules(base, List.of(), deductions), new Unlimited());
    }

    public RulebookDefinition.Scoring definition() {
        return new RulebookDefinition.Scoring(definitionsOf(scoreRules.base()),
                new RulebookDefinition.Bonuses(definitionsOf(scoreRules.bonuses()), bonusLimit.definition()),
                definitionsOf(scoreRules.deductions()));
    }

    private static List<RuleDefinition> definitionsOf(List<? extends ScoreRule> rules) {
        List<RuleDefinition> definitions = new ArrayList<>();
        for (ScoreRule rule : rules) {
            definitions.add(rule.definition());
        }
        return definitions;
    }

    public ScoreBreakdown evaluate(RawMetrics captured) {
        RuleEvaluation bonusesObtained = RuleEvaluation.combining(scoreRules.bonuses(), captured);
        return new ScoreBreakdown(
                RuleEvaluation.combining(scoreRules.base(), captured),
                RuleEvaluation.concat(List.of(bonusesObtained, bonusLimit.limit(bonusesObtained.total()))),
                RuleEvaluation.combining(scoreRules.deductions(), captured));
    }

    public Set<ResultSource> requiredSources() {
        Set<ResultSource> sources = EnumSet.noneOf(ResultSource.class);
        for (ScoreRule rule : scoreRules.all()) {
            sources.add(rule.source());
        }
        return Set.copyOf(sources);
    }

    /**
     * What the rules of each source contributed. The bonus cap applies to the whole score, so it belongs to no source.
     */
    public List<SourceContribution> contributions(RawMetrics captured) {
        List<SourceContribution> contributions = new ArrayList<>();
        for (ResultSource source : ResultSource.values()) {
            List<ScoreRule> fromSource = scoreRules.all().stream().filter(rule -> rule.source() == source).toList();
            if (!fromSource.isEmpty()) {
                contributions.add(new SourceContribution(source, RuleEvaluation.combining(fromSource, captured)));
            }
        }
        return List.copyOf(contributions);
    }
}
