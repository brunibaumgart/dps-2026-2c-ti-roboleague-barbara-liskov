package com.roboleague.evaluation.definition;

import com.roboleague.evaluation.Metric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

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

    /**
     * What these arguments carry that a rule built from them does not take: unknown parameters, unknown metric
     * roles, and nested rules for a rule that takes none.
     */
    public List<String> unknownTo(RuleArguments accepted) {
        List<String> unknown = new ArrayList<>();
        for (String name : numbers.unknownTo(accepted.numbers())) {
            unknown.add("unknown parameter '" + name + "'");
        }
        for (String role : new TreeSet<>(metrics.keySet())) {
            if (!accepted.metrics().containsKey(role)) {
                unknown.add("unknown metric '" + role + "'");
            }
        }
        if (!rules.isEmpty() && accepted.rules().isEmpty()) {
            unknown.add("rules nested in a rule that takes none");
        }
        return unknown;
    }

    public Metric metric(String role) {
        Metric metric = metrics.get(role);
        if (metric == null) {
            throw new IllegalArgumentException("missing metric '" + role + "'");
        }
        return metric;
    }
}
