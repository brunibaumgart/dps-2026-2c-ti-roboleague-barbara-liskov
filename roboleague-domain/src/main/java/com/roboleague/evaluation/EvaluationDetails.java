package com.roboleague.evaluation;

import java.util.Objects;

/**
 * Value object representing explanation formula and metric evidence for a score item.
 */
public record EvaluationDetails(String rawMetric, String appliedFormula) {
    public EvaluationDetails {
        Objects.requireNonNull(rawMetric, "rawMetric cannot be null");
        Objects.requireNonNull(appliedFormula, "appliedFormula cannot be null");
    }

    public static EvaluationDetails of(String rawMetric, String appliedFormula) {
        return new EvaluationDetails(rawMetric, appliedFormula);
    }
}
