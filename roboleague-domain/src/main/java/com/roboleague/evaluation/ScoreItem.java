package com.roboleague.evaluation;

import java.util.Objects;

/**
 * Immutable line-item representing a single contribution to the final score.
 */
public record ScoreItem(
        String concept,
        String rawMetric,
        String appliedFormula,
        double subtotal
) {
    public ScoreItem {
        Objects.requireNonNull(concept, "concept cannot be null");
        Objects.requireNonNull(rawMetric, "rawMetric cannot be null");
        Objects.requireNonNull(appliedFormula, "appliedFormula cannot be null");
    }
}
