package com.roboleague.evaluation;

import com.roboleague.scheduling.SlotId;

import java.util.Objects;

/**
 * Identity of an attempt, given by its turn: the n-th attempt in a slot. The domain numbers it in arrival order
 * ({@link TurnAttempts}), never whoever captures: a result reaches the attempt still awaiting a source, or opens the
 * next one while the rulebook admits it, so no capture replaces an earlier attempt.
 */
public record AttemptId(SlotId slotId, int number) {
    private static final char SEPARATOR = '-';

    public AttemptId {
        Objects.requireNonNull(slotId, "slotId cannot be null");
        if (number < 1) {
            throw new IllegalArgumentException("attempt number must be positive: " + number);
        }
    }

    public static AttemptId of(SlotId slotId, int number) {
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
        return new AttemptId(SlotId.of(value.substring(0, separator)), Integer.parseInt(number));
    }

    public String value() {
        return slotId.value() + SEPARATOR + number;
    }

    @Override
    public String toString() {
        return value();
    }
}
