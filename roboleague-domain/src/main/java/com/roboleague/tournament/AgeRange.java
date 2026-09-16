package com.roboleague.tournament;

/**
 * Value object representing an acceptable age range for participants.
 */
public record AgeRange(int minAge, int maxAge) {
    public AgeRange {
        if (minAge < 0 || maxAge < minAge) {
            throw new IllegalArgumentException("Invalid age limits: minAge=" + minAge + ", maxAge=" + maxAge);
        }
    }

    public boolean contains(int age) {
        return age >= minAge && age <= maxAge;
    }

    public static AgeRange of(int minAge, int maxAge) {
        return new AgeRange(minAge, maxAge);
    }
}
