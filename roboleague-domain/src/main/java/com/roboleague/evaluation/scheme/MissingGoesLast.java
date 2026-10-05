package com.roboleague.evaluation.scheme;

import java.util.OptionalDouble;

/**
 * Orders optional values so that a team without the value (no rounds) always goes after one that has it.
 */
final class MissingGoesLast {

    private MissingGoesLast() {
    }

    static int lowerFirst(OptionalDouble a, OptionalDouble b) {
        if (a.isPresent() && b.isPresent()) {
            return Double.compare(a.getAsDouble(), b.getAsDouble());
        }
        return Boolean.compare(a.isEmpty(), b.isEmpty());
    }

    static int higherFirst(OptionalDouble a, OptionalDouble b) {
        if (a.isPresent() && b.isPresent()) {
            return Double.compare(b.getAsDouble(), a.getAsDouble());
        }
        return Boolean.compare(a.isEmpty(), b.isEmpty());
    }
}
