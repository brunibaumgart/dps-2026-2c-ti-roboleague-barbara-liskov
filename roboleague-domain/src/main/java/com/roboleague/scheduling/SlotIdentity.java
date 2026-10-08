package com.roboleague.scheduling;

import com.roboleague.tournament.TeamId;

import java.util.Objects;

/**
 * Value object representing identity context of a slot.
 */
public record SlotIdentity(SlotId slotId, RoundId roundId, TeamId teamId) {
    public SlotIdentity {
        Objects.requireNonNull(slotId, "slotId cannot be null");
        Objects.requireNonNull(roundId, "roundId cannot be null");
        Objects.requireNonNull(teamId, "teamId cannot be null");
    }

    public static SlotIdentity of(SlotId slotId, RoundId roundId, TeamId teamId) {
        return new SlotIdentity(slotId, roundId, teamId);
    }
}
