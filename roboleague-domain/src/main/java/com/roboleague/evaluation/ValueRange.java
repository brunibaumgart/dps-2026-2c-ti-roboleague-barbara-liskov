package com.roboleague.evaluation;

import com.roboleague.evaluation.definition.Parameters;

/**
 * Values a declared metric may take, both ends included. Without a maximum the range is open upwards.
 */
public record ValueRange(double min, double max) {
    private static final String MIN = "min";
    private static final String MAX = "max";

    public ValueRange {
        if (!Double.isFinite(min)) {
            throw new IllegalArgumentException("min must be a finite number: " + min);
        }
        if (Double.isNaN(max) || max < min) {
            throw new IllegalArgumentException("max cannot be less than min: " + max + " < " + min);
        }
    }

    public static ValueRange between(double min, double max) {
        if (!Double.isFinite(max)) {
            throw new IllegalArgumentException("max must be a finite number: " + max);
        }
        return new ValueRange(min, max);
    }

    public static ValueRange atLeast(double min) {
        return new ValueRange(min, Double.POSITIVE_INFINITY);
    }

    public static ValueRange from(Parameters numbers) {
        double min = numbers.number(MIN);
        return numbers.values().containsKey(MAX) ? between(min, numbers.number(MAX)) : atLeast(min);
    }

    public Parameters definition() {
        Parameters numbers = Parameters.none().with(MIN, min);
        return Double.isFinite(max) ? numbers.with(MAX, max) : numbers;
    }

    public boolean contains(double value) {
        return value >= min && value <= max;
    }

    public String describe() {
        return Double.isFinite(max) ? "between " + min + " and " + max : "at least " + min;
    }
}
