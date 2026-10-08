package com.roboleague.scheduling;

import com.roboleague.tournament.TeamId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/** Shared occupation rule for aggregate validation and scheduling across rounds. */
final class SlotConflicts {
    private SlotConflicts() { }

    static Optional<LocalDateTime> blockedUntil(Slot candidate, Slot occupied) {
        if (candidate.getStatus() == Slot.SlotStatus.CANCELLED
                || occupied.getStatus() == Slot.SlotStatus.CANCELLED) {
            return Optional.empty();
        }
        return blockedUntil(candidate.getTeamId(), new SlotAssignment(candidate.getTrack(), candidate.getAssignedJudges()),
                candidate.getTimeWindow(), candidate.getTrackInterval(), occupied);
    }

    static Optional<LocalDateTime> blockedUntil(TeamId teamId, SlotAssignment assignment, TimeWindow window,
                                               Duration trackInterval, Slot occupied) {
        if (occupied.getStatus() == Slot.SlotStatus.CANCELLED) { return Optional.empty(); }
        LocalDateTime blocked = null;
        if (assignment.track().id().equals(occupied.getTrack().id())
                && overlaps(window.startTime(), window.endTime().plus(trackInterval),
                            occupied.getStartTime(), occupied.getTrackAvailableAt())) {
            blocked = occupied.getTrackAvailableAt();
        }
        boolean sharesJudge = assignment.assignedJudges().stream()
                .anyMatch(judge -> occupied.isJudgedBy(judge.id()));
        if ((sharesJudge || teamId.equals(occupied.getTeamId()))
                && overlaps(window.startTime(), window.endTime(),
                            occupied.getStartTime(), occupied.getEndTime())) {
            if (blocked == null || occupied.getEndTime().isAfter(blocked)) {
                blocked = occupied.getEndTime();
            }
        }
        return Optional.ofNullable(blocked);
    }

    private static boolean overlaps(LocalDateTime start, LocalDateTime end,
                                    LocalDateTime otherStart, LocalDateTime otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }
}
