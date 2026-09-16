package com.roboleague.tournament;

/**
 * Value object representing weight in grams.
 */
public record Weight(double grams) {
    public Weight {
        if (grams <= 0) {
            throw new IllegalArgumentException("grams must be positive");
        }
    }

    public boolean isGreaterThan(Weight other) {
        return this.grams > other.grams;
    }

    public static Weight ofGrams(double grams) {
        return new Weight(grams);
    }
}
