package com.roboleague.scheduling;

import java.util.List;
import java.util.Objects;

/**
 * Value object representing track and judge assignment for a slot.
 */
public record SlotAssignment(Track track, List<Judge> assignedJudges) {
    public SlotAssignment {
        Objects.requireNonNull(track, "track cannot be null");
        if (!track.isActive()) {
            throw new IllegalArgumentException("Slot requires an active track");
        }
        assignedJudges = List.copyOf(Objects.requireNonNull(assignedJudges, "assignedJudges cannot be null"));
        if (assignedJudges.isEmpty()
                || assignedJudges.stream().map(Judge::id).distinct().count() != assignedJudges.size()) {
            throw new IllegalArgumentException("Slot requires distinct assigned judges");
        }
    }

    public static SlotAssignment of(Track track, List<Judge> assignedJudges) {
        return new SlotAssignment(track, assignedJudges);
    }
}
