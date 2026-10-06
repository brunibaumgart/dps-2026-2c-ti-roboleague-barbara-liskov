package com.roboleague.evaluation.definition;

import com.roboleague.evaluation.MeasurementUnit;
import com.roboleague.evaluation.Metric;

import java.util.Objects;

/**
 * Description of a metric a rulebook declares, as it travels through the API and the database:
 * the metric, its unit and its range as numbers ("min" and, when bounded, "max").
 */
public record MetricDeclaration(Metric metric, MeasurementUnit unit, Parameters range) {
    public MetricDeclaration {
        Objects.requireNonNull(metric, "metric cannot be null");
        Objects.requireNonNull(unit, "unit cannot be null");
        Objects.requireNonNull(range, "range cannot be null");
    }
}
