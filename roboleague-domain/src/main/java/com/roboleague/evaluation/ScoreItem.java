package com.roboleague.evaluation;

import java.util.Objects;

public record ScoreItem(
        String concept,
        EvaluationDetails details,
        Points subtotal
) {
    public ScoreItem {
        Objects.requireNonNull(concept, "concept cannot be null");
        Objects.requireNonNull(details, "details cannot be null");
        Objects.requireNonNull(subtotal, "subtotal cannot be null");
    }

    public String rawMetric() {
        return details.rawMetric();
    }

    public String appliedFormula() {
        return details.appliedFormula();
    }

    public static ScoreItem of(String concept, EvaluationDetails details, Points subtotal) {
        return new ScoreItem(concept, details, subtotal);
    }

    public static ScoreItem of(String concept, String rawMetric, String appliedFormula, Points subtotal) {
        return new ScoreItem(concept, new EvaluationDetails(rawMetric, appliedFormula), subtotal);
    }
}
