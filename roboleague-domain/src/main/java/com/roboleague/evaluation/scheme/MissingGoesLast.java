package com.roboleague.evaluation.scheme;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Orders optional values so that a team without the value (no rounds) always goes after one that has it.
 */
final class MissingGoesLast {

    private MissingGoesLast() {
    }

    static <T extends Comparable<? super T>> int lowerFirst(Optional<T> a, Optional<T> b) {
        if (a.isPresent() && b.isPresent()) {
            return a.get().compareTo(b.get());
        }
        return Boolean.compare(a.isEmpty(), b.isEmpty());
    }

    static int lowerFirst(OptionalDouble a, OptionalDouble b) {
        return lowerFirst(boxed(a), boxed(b));
    }

    static int higherFirst(OptionalDouble a, OptionalDouble b) {
        if (a.isPresent() && b.isPresent()) {
            return Double.compare(b.getAsDouble(), a.getAsDouble());
        }
        return Boolean.compare(a.isEmpty(), b.isEmpty());
    }

    private static Optional<Double> boxed(OptionalDouble value) {
        return value.isPresent() ? Optional.of(value.getAsDouble()) : Optional.empty();
    }
}
