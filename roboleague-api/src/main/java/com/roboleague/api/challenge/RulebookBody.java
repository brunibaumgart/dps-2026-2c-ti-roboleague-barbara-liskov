package com.roboleague.api.challenge;

import com.roboleague.evaluation.MeasurementUnit;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.definition.MetricDeclaration;
import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.RuleArguments;
import com.roboleague.evaluation.definition.RuleDefinition;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.evaluation.definition.StrategyDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON shape of a rulebook, the same when it is sent and when it is read back. It only translates to and from
 * {@link RulebookDefinition}; which class each "type" becomes is decided by the domain's rule catalog.
 */
record RulebookBody(List<MetricDeclarationBody> metrics, ScoringBody scoring, RankingBody ranking) {

    record MetricDeclarationBody(String name, String source, String unit, Map<String, Double> range) {
    }

    record ScoringBody(List<RuleBody> rules, List<RuleBody> bonuses, StrategyBody bonusLimit) {
    }

    record RankingBody(StrategyBody roundSelection, List<String> criteria) {
    }

    record RuleBody(String type, String name, Map<String, Double> numbers, Map<String, MetricBody> metrics,
                    List<RuleBody> rules) {
    }

    record MetricBody(String name, String source) {
    }

    record StrategyBody(String type, Map<String, Double> numbers) {
    }

    RulebookDefinition toDefinition() {
        if (scoring == null || ranking == null || scoring.bonusLimit() == null || ranking.roundSelection() == null) {
            throw new IllegalArgumentException("a rulebook needs scoring (with bonusLimit) and ranking (with roundSelection)");
        }
        return new RulebookDefinition(toDeclarations(metrics),
                new RulebookDefinition.Scoring(toRules(scoring.rules()), toRules(scoring.bonuses()),
                        toStrategy(scoring.bonusLimit())),
                new RulebookDefinition.Ranking(toStrategy(ranking.roundSelection()), orEmpty(ranking.criteria())));
    }

    static RulebookBody from(RulebookDefinition definition) {
        return new RulebookBody(fromDeclarations(definition.metrics()),
                new ScoringBody(fromRules(definition.scoring().rules()), fromRules(definition.scoring().bonuses()),
                        fromStrategy(definition.scoring().bonusLimit())),
                new RankingBody(fromStrategy(definition.ranking().roundSelection()), definition.ranking().criteria()));
    }

    private static List<MetricDeclaration> toDeclarations(List<MetricDeclarationBody> bodies) {
        List<MetricDeclaration> declarations = new ArrayList<>();
        for (MetricDeclarationBody body : orEmpty(bodies)) {
            if (body == null || body.name() == null) {
                throw new IllegalArgumentException("a declared metric needs a name");
            }
            declarations.add(new MetricDeclaration(toMetric(body.name(), new MetricBody(body.name(), body.source())),
                    toUnit(body.name(), body.unit()), Parameters.of(orEmpty(body.range()))));
        }
        return declarations;
    }

    private static MeasurementUnit toUnit(String metric, String unit) {
        if (unit == null) {
            throw new IllegalArgumentException("metric '" + metric + "' needs a unit");
        }
        try {
            return MeasurementUnit.valueOf(unit);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("metric '" + metric + "': unknown unit '" + unit
                    + "', expected one of " + List.of(MeasurementUnit.values()));
        }
    }

    private static List<RuleDefinition> toRules(List<RuleBody> bodies) {
        List<RuleDefinition> rules = new ArrayList<>();
        for (RuleBody body : orEmpty(bodies)) {
            if (body == null) {
                throw new IllegalArgumentException("a rule cannot be empty");
            }
            Map<String, Metric> metrics = new HashMap<>();
            orEmpty(body.metrics()).forEach((role, metric) -> metrics.put(role, toMetric(role, metric)));
            rules.add(new RuleDefinition(body.type(), body.name(),
                    new RuleArguments(Parameters.of(orEmpty(body.numbers())), metrics, toRules(body.rules()))));
        }
        return rules;
    }

    private static Metric toMetric(String role, MetricBody body) {
        if (body == null || body.name() == null || body.source() == null) {
            throw new IllegalArgumentException("metric '" + role + "' needs a name and a source");
        }
        try {
            return new Metric(body.name(), ResultSource.valueOf(body.source()));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("metric '" + role + "': unknown source '" + body.source()
                    + "', expected one of " + List.of(ResultSource.values()));
        }
    }

    private static StrategyDefinition toStrategy(StrategyBody body) {
        return new StrategyDefinition(body.type(), Parameters.of(orEmpty(body.numbers())));
    }

    private static List<MetricDeclarationBody> fromDeclarations(List<MetricDeclaration> declarations) {
        List<MetricDeclarationBody> bodies = new ArrayList<>();
        for (MetricDeclaration declaration : declarations) {
            bodies.add(new MetricDeclarationBody(declaration.metric().name(), declaration.metric().source().name(),
                    declaration.unit().name(), declaration.range().values()));
        }
        return bodies;
    }

    private static List<RuleBody> fromRules(List<RuleDefinition> rules) {
        List<RuleBody> bodies = new ArrayList<>();
        for (RuleDefinition rule : rules) {
            Map<String, MetricBody> metrics = new HashMap<>();
            rule.arguments().metrics().forEach((role, metric) ->
                    metrics.put(role, new MetricBody(metric.name(), metric.source().name())));
            bodies.add(new RuleBody(rule.type(), rule.name(), rule.arguments().numbers().values(), metrics,
                    fromRules(rule.arguments().rules())));
        }
        return bodies;
    }

    private static StrategyBody fromStrategy(StrategyDefinition strategy) {
        return new StrategyBody(strategy.type(), strategy.numbers().values());
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static <K, V> Map<K, V> orEmpty(Map<K, V> map) {
        return map == null ? Map.of() : map;
    }
}
