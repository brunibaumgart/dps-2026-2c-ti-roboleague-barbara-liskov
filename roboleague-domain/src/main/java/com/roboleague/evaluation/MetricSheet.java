package com.roboleague.evaluation;

import com.roboleague.evaluation.definition.MetricDeclaration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What a rulebook measures: the metrics that have to be captured for an attempt, each with its unit and range.
 */
public record MetricSheet(List<MetricDefinition> metrics) {
    public MetricSheet {
        metrics = List.copyOf(Objects.requireNonNull(metrics, "metrics cannot be null"));
        Set<String> names = new HashSet<>();
        for (MetricDefinition metric : metrics) {
            if (!names.add(metric.name())) {
                throw new IllegalArgumentException("metric '" + metric.name() + "' is declared twice");
            }
        }
    }

    public static MetricSheet none() {
        return new MetricSheet(List.of());
    }

    public List<MetricDeclaration> declarations() {
        List<MetricDeclaration> declarations = new ArrayList<>();
        for (MetricDefinition metric : metrics) {
            declarations.add(metric.declaration());
        }
        return declarations;
    }
}
