package com.roboleague.evaluation;

import java.util.Objects;

/**
 * Identity of an attempt, given by its turn: the n-th attempt in a slot. The domain derives it, so two captures
 * for the same turn reach the same attempt instead of creating a second one that replaces the first.
 */
public record AttemptId(String slotId, int number) {
    private static final char SEPARATOR = '-';

    public AttemptId {
        Objects.requireNonNull(slotId, "slotId cannot be null");
        if (slotId.isBlank()) {
            throw new IllegalArgumentException("slotId cannot be blank");
        }
        if (number < 1) {
            throw new IllegalArgumentException("attempt number must be positive: " + number);
        }
    }

    public static AttemptId of(String slotId, int number) {
        return new AttemptId(slotId, number);
    }

    /**
     * Reads an id written by {@link #value()}: the slot, a dash and the attempt number ({@code "slot-7-1"}).
     */
    public static AttemptId parse(String value) {
        Objects.requireNonNull(value, "attempt id cannot be null");
        int separator = value.lastIndexOf(SEPARATOR);
        String number = value.substring(separator + 1);
        if (separator < 1 || number.isEmpty() || !number.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("attempt id must be <slot>-<number>: " + value);
        }
        return new AttemptId(value.substring(0, separator), Integer.parseInt(number));
    }

    public String value() {
        return slotId + SEPARATOR + number;
    }

    @Override
    public String toString() {
        return value();
    }
}
