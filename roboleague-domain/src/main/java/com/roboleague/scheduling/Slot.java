package com.roboleague.scheduling;

import com.roboleague.tournament.TeamId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Scheduled slot (turn) for a team on a specific track with assigned judges.
 */
public final class Slot {

    public enum SlotStatus {
        SCHEDULED,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }

    private final SlotIdentity identity;
    private final Track track;
    private final TimeWindow timeWindow;
    private final List<Judge> assignedJudges;
    private final SlotStatus status;
    private final Duration trackInterval;

    public Slot(SlotIdentity identity, SlotAssignment assignment, TimeWindow timeWindow) {
        this(identity, assignment, timeWindow, Duration.ZERO, SlotStatus.SCHEDULED);
    }

    public Slot(SlotIdentity identity, SlotAssignment assignment, TimeWindow timeWindow,
                Duration trackInterval) {
        this(identity, assignment, timeWindow, trackInterval, SlotStatus.SCHEDULED);
    }

    private Slot(SlotIdentity identity, SlotAssignment assignment, TimeWindow timeWindow,
                 Duration trackInterval, SlotStatus status) {
        this.identity = Objects.requireNonNull(identity, "identity cannot be null");
        Objects.requireNonNull(assignment, "assignment cannot be null");
        this.track = assignment.track();
        this.assignedJudges = assignment.assignedJudges();
        this.timeWindow = Objects.requireNonNull(timeWindow, "timeWindow cannot be null");
        this.trackInterval = Objects.requireNonNull(trackInterval, "trackInterval cannot be null");
        if (timeWindow.duration().isZero() || trackInterval.isNegative()) {
            throw new IllegalArgumentException("Slot duration must be positive and track interval nonnegative");
        }
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    /** Rehydrates persisted state without replaying transitions. */
    public static Slot restore(SlotIdentity identity, SlotAssignment assignment, TimeWindow timeWindow,
                               Duration trackInterval, SlotStatus status) {
        return new Slot(identity, assignment, timeWindow, trackInterval, status);
    }

    public Duration getTrackInterval() {
        return trackInterval;
    }

    public LocalDateTime getTrackAvailableAt() {
        return getEndTime().plus(trackInterval);
    }

    public SlotIdentity getIdentity() {
        return identity;
    }

    public SlotId getSlotId() {
        return identity.slotId();
    }

    public RoundId getRoundId() {
        return identity.roundId();
    }

    public TeamId getTeamId() {
        return identity.teamId();
    }

    public Track getTrack() {
        return track;
    }

    public TimeWindow getTimeWindow() {
        return timeWindow;
    }

    public LocalDateTime getStartTime() {
        return timeWindow.startTime();
    }

    public LocalDateTime getEndTime() {
        return timeWindow.endTime();
    }

    public List<Judge> getAssignedJudges() {
        return assignedJudges;
    }

    public boolean isJudgedBy(JudgeId judgeId) {
        return assignedJudges.stream().anyMatch(judge -> judge.id().equals(judgeId));
    }

    public SlotStatus getStatus() {
        return status;
    }

    // Only the aggregate root may perform slot transitions.
    Slot transitionTo(SlotStatus next) {
        boolean allowed = status == SlotStatus.SCHEDULED
                && (next == SlotStatus.IN_PROGRESS || next == SlotStatus.CANCELLED)
                || status == SlotStatus.IN_PROGRESS && next == SlotStatus.COMPLETED;
        if (!allowed) {
            throw new IllegalStateException("Cannot transition slot from " + status + " to " + next);
        }
        return restore(identity, new SlotAssignment(track, assignedJudges), timeWindow, trackInterval, next);
    }

    public static Slot of(SlotIdentity identity, SlotAssignment assignment, TimeWindow timeWindow) {
        return new Slot(identity, assignment, timeWindow);
    }
}
