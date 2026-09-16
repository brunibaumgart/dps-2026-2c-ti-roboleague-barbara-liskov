package com.roboleague.tournament;

/**
 * Value object representing 3D dimensions in millimeters.
 */
public record Dimensions(double lengthMm, double widthMm, double heightMm) {
    public Dimensions {
        if (lengthMm <= 0 || widthMm <= 0 || heightMm <= 0) {
            throw new IllegalArgumentException("All dimensions must be positive");
        }
    }

    public boolean fitsWithin(Dimensions limits) {
        return this.lengthMm <= limits.lengthMm
                && this.widthMm <= limits.widthMm
                && this.heightMm <= limits.heightMm;
    }

    public static Dimensions of(double lengthMm, double widthMm, double heightMm) {
        return new Dimensions(lengthMm, widthMm, heightMm);
    }
}
