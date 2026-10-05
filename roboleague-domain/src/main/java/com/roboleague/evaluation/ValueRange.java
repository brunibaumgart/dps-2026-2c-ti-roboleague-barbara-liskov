package com.roboleague.evaluation;

/**
 * Values a declared metric may take, both ends included. Without a maximum the range is open upwards.
 */
public record ValueRange(double min, double max) {
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

    public boolean contains(double value) {
        return value >= min && value <= max;
    }

    public String describe() {
        return Double.isFinite(max) ? "between " + min + " and " + max : "at least " + min;
    }
}
