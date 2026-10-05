package com.roboleague.evaluation;

import com.roboleague.evaluation.definition.MetricDeclaration;

import java.util.Objects;
import java.util.Optional;

/**
 * A metric a rulebook declares: what is measured, in which unit and which values it may take.
 * Captured measurements are checked against it before an attempt is scored.
 */
public record MetricDefinition(Metric metric, MeasurementUnit unit, ValueRange range) {
    public MetricDefinition {
        Objects.requireNonNull(metric, "metric cannot be null");
        Objects.requireNonNull(unit, "unit cannot be null");
        Objects.requireNonNull(range, "range cannot be null");
    }

    public static MetricDefinition from(MetricDeclaration declaration) {
        return new MetricDefinition(declaration.metric(), declaration.unit(), ValueRange.from(declaration.range()));
    }

    public MetricDeclaration declaration() {
        return new MetricDeclaration(metric, unit, range.definition());
    }

    public String name() {
        return metric.name();
    }

    /**
     * Why a captured value cannot be taken for this metric, if it cannot.
     */
    public Optional<String> problemWith(double value) {
        if (!Double.isFinite(value)) {
            return Optional.of(problem("must be a finite number", value));
        }
        if (!unit.accepts(value)) {
            return Optional.of(problem("is not a valid " + unit, value));
        }
        if (!range.contains(value)) {
            return Optional.of(problem("must be " + range.describe(), value));
        }
        return Optional.empty();
    }

    private String problem(String reason, double value) {
        return "measurement '" + metric.name() + "' " + reason + ": " + value;
    }
}
