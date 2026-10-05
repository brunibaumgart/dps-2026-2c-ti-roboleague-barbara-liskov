package com.roboleague.evaluation;

/**
 * Sequential version of a challenge rulebook. The challenge assigns it when publishing.
 */
public record RulebookVersion(int number) {
    public RulebookVersion {
        if (number < 1) {
            throw new IllegalArgumentException("number must be positive");
        }
    }

    public static RulebookVersion first() {
        return new RulebookVersion(1);
    }

    public RulebookVersion next() {
        return new RulebookVersion(number + 1);
    }

    @Override
    public String toString() {
        return "v" + number;
    }
}
