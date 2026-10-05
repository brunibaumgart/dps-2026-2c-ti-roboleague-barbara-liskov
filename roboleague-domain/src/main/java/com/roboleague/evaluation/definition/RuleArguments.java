package com.roboleague.evaluation.definition;

import com.roboleague.evaluation.Metric;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a rule needs besides its type and name: numeric parameters, the metrics it reads and, for a composite, its rules.
 */
public record RuleArguments(Parameters numbers, Map<String, Metric> metrics, List<RuleDefinition> rules) {
    public RuleArguments {
        Objects.requireNonNull(numbers, "numbers cannot be null");
        metrics = Map.copyOf(Objects.requireNonNull(metrics, "metrics cannot be null"));
        rules = List.copyOf(Objects.requireNonNull(rules, "rules cannot be null"));
    }

    public static RuleArguments of(Parameters numbers) {
        return new RuleArguments(numbers, Map.of(), List.of());
    }

    public static RuleArguments ofRules(List<RuleDefinition> rules) {
        return new RuleArguments(Parameters.none(), Map.of(), rules);
    }

    public RuleArguments withMetric(String role, Metric metric) {
        Map<String, Metric> extended = new HashMap<>(metrics);
        extended.put(role, metric);
        return new RuleArguments(numbers, extended, rules);
    }

    public Metric metric(String role) {
        Metric metric = metrics.get(role);
        if (metric == null) {
            throw new IllegalArgumentException("missing metric '" + role + "'");
        }
        return metric;
    }
}
