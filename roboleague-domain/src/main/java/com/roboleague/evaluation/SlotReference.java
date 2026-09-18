package com.roboleague.evaluation;

import java.util.Objects;

public record SlotReference(String slotId, String roundId) {
    public SlotReference {
        Objects.requireNonNull(slotId, "slotId cannot be null");
        Objects.requireNonNull(roundId, "roundId cannot be null");
    }

    public static SlotReference of(String slotId, String roundId) {
        return new SlotReference(slotId, roundId);
    }
}
