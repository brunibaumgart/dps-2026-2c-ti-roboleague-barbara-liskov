package com.roboleague.evaluation;

import java.util.Objects;

public record EvaluationDetails(String rawMetric, String appliedFormula) {
    public EvaluationDetails {
        Objects.requireNonNull(rawMetric, "rawMetric cannot be null");
        Objects.requireNonNull(appliedFormula, "appliedFormula cannot be null");
    }
}
