package com.roboleague.scheduling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Value object representing track and judge assignment for a slot.
 */
public record SlotAssignment(Track track, List<Judge> assignedJudges) {
    public SlotAssignment {
        Objects.requireNonNull(track, "track cannot be null");
        assignedJudges = assignedJudges != null ? Collections.unmodifiableList(new ArrayList<>(assignedJudges)) : Collections.emptyList();
    }

    public static SlotAssignment of(Track track, List<Judge> assignedJudges) {
        return new SlotAssignment(track, assignedJudges);
    }
}
