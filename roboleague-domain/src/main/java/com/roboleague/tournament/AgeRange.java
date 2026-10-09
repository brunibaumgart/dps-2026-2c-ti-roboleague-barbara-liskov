package com.roboleague.tournament;

/**
 * Value object representing an acceptable age range for participants.
 * Bounds are inclusive; a null maximum means there is no upper age limit.
 */
public record AgeRange(int minAge, Integer maxAge) {
    public AgeRange {
        if (minAge < 0 || (maxAge != null && maxAge < minAge)) {
            throw new IllegalArgumentException("Invalid age limits: minAge=" + minAge + ", maxAge=" + maxAge);
        }
    }

    public boolean contains(int age) {
        return age >= minAge && (maxAge == null || age <= maxAge);
    }

    public static AgeRange of(int minAge, Integer maxAge) {
        return new AgeRange(minAge, maxAge);
    }
}
