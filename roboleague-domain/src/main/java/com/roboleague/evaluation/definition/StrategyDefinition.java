package com.roboleague.evaluation.definition;

import java.util.Objects;

/**
 * Description of a round selection or a bonus limit: its type and its numeric parameters.
 */
public record StrategyDefinition(String type, Parameters numbers) {
    public StrategyDefinition {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("a strategy needs a type");
        }
        Objects.requireNonNull(numbers, "numbers cannot be null");
    }
}
