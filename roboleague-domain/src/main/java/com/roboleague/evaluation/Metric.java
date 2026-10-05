package com.roboleague.evaluation;

import java.util.Objects;

/**
 * A named measurement a rule reads from the captured metrics, and the source that provides it.
 */
public record Metric(String name, ResultSource source) {
    public Metric {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
    }

    public static Metric sensor(String name) {
        return new Metric(name, ResultSource.AUTOMATIC_MEASUREMENTS);
    }

    public static Metric judged(String name) {
        return new Metric(name, ResultSource.JUDGE_PANEL);
    }
}
