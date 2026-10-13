package com.roboleague.tournament;

/**
 * Value object representing 3D dimensions in millimeters.
 */
public record Dimensions(double lengthMm, double widthMm, double heightMm) {
    public Dimensions {
        if (!Double.isFinite(lengthMm) || !Double.isFinite(widthMm) || !Double.isFinite(heightMm)
                || lengthMm <= 0 || widthMm <= 0 || heightMm <= 0) {
            throw new IllegalArgumentException("All dimensions must be positive");
        }
    }

    public static Dimensions of(double lengthMm, double widthMm, double heightMm) {
        return new Dimensions(lengthMm, widthMm, heightMm);
    }
}
