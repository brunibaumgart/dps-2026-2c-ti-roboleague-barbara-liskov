package com.roboleague.evaluation;

import com.roboleague.evaluation.definition.MetricDeclaration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

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

    /**
     * Checks what one source sent against the metrics declared for it: none missing, none undeclared and each one
     * in its unit and range. A mixed challenge checks each source when it arrives (F3).
     */
    public MeasurementCheck check(ResultSource source, Map<String, Double> measurements) {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(measurements, "measurements cannot be null");
        List<MetricDefinition> fromSource = metrics.stream()
                .filter(metric -> metric.metric().source() == source)
                .toList();
        List<String> problems = new ArrayList<>();
        for (MetricDefinition metric : fromSource) {
            Double value = measurements.get(metric.name());
            if (value == null) {
                problems.add("measurement '" + metric.name() + "' is missing");
            } else {
                metric.problemWith(value).ifPresent(problems::add);
            }
        }
        for (String name : new TreeSet<>(measurements.keySet())) {
            if (fromSource.stream().noneMatch(metric -> metric.name().equals(name))) {
                problems.add("measurement '" + name + "' is not declared for " + source);
            }
        }
        return problems.isEmpty() ? new MeasurementCheck.Accepted() : new MeasurementCheck.Rejected(problems);
    }

    public List<MetricDeclaration> declarations() {
        List<MetricDeclaration> declarations = new ArrayList<>();
        for (MetricDefinition metric : metrics) {
            declarations.add(metric.declaration());
        }
        return declarations;
    }
}
