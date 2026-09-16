package com.roboleague.evaluation;

import java.util.Objects;

/**
 * Value object referencing the scheduled slot and round where an attempt takes place.
 */
public record SlotReference(String slotId, String roundId) {
    public SlotReference {
        Objects.requireNonNull(slotId, "slotId cannot be null");
        Objects.requireNonNull(roundId, "roundId cannot be null");
    }

    public static SlotReference of(String slotId, String roundId) {
        return new SlotReference(slotId, roundId);
    }
}
