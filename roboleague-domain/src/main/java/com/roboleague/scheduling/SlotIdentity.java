package com.roboleague.scheduling;

import java.util.Objects;

/**
 * Value object representing identity context of a slot.
 */
public record SlotIdentity(String slotId, String roundId, String teamId) {
    public SlotIdentity {
        Objects.requireNonNull(slotId, "slotId cannot be null");
        Objects.requireNonNull(roundId, "roundId cannot be null");
        Objects.requireNonNull(teamId, "teamId cannot be null");
    }

    public static SlotIdentity of(String slotId, String roundId, String teamId) {
        return new SlotIdentity(slotId, roundId, teamId);
    }
}
