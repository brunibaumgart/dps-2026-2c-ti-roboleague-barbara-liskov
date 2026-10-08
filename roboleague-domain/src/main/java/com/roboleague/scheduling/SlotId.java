package com.roboleague.scheduling;

import java.util.Objects;

/** Textual identity of a slot; preserves the supplied external value. */
public record SlotId(String value) {
    public SlotId {
        Objects.requireNonNull(value, "slotId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("slotId cannot be blank");
        }
    }

    public static SlotId of(String value) {
        return new SlotId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
