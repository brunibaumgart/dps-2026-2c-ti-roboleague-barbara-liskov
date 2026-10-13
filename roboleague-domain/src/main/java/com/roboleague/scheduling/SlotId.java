package com.roboleague.scheduling;

import com.roboleague.support.Identifiers;

/** Textual identity of a slot; preserves the supplied external value. */
public record SlotId(String value) {
    public SlotId {
        Identifiers.validate(value, "slotId");
    }

    public static SlotId of(String value) {
        return new SlotId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
