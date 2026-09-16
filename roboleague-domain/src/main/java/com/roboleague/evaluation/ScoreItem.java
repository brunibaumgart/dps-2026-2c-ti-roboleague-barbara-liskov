package com.roboleague.evaluation;

import java.util.Objects;

/**
 * Immutable line-item representing a single contribution to the final score.
 */
public record ScoreItem(
        String concept,
        EvaluationDetails details,
        double subtotal
) {
    public ScoreItem {
        Objects.requireNonNull(concept, "concept cannot be null");
        Objects.requireNonNull(details, "details cannot be null");
    }

    public String rawMetric() {
        return details.rawMetric();
    }

    public String appliedFormula() {
        return details.appliedFormula();
    }

    public static ScoreItem of(String concept, EvaluationDetails details, double subtotal) {
        return new ScoreItem(concept, details, subtotal);
    }

    public static ScoreItem of(String concept, String rawMetric, String appliedFormula, double subtotal) {
        return new ScoreItem(concept, new EvaluationDetails(rawMetric, appliedFormula), subtotal);
    }
}
