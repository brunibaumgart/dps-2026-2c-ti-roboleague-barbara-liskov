package com.roboleague.scheduling;

import com.roboleague.tournament.TeamId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    private final SlotCancellation cancellation;

    public Slot(SlotIdentity identity, SlotAssignment assignment, TimeWindow timeWindow) {
        this(identity, assignment, timeWindow, Duration.ZERO, SlotStatus.SCHEDULED, null);
    }

    public Slot(SlotIdentity identity, SlotAssignment assignment, TimeWindow timeWindow,
                Duration trackInterval) {
        this(identity, assignment, timeWindow, trackInterval, SlotStatus.SCHEDULED, null);
    }

    private Slot(SlotIdentity identity, SlotAssignment assignment, TimeWindow timeWindow,
                 Duration trackInterval, SlotStatus status, SlotCancellation cancellation) {
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
        if ((status == SlotStatus.CANCELLED) != (cancellation != null)) {
            throw new IllegalArgumentException("Exactly the cancelled slots record who cancelled them and why");
        }
        this.cancellation = cancellation;
    }

    /**
     * Rehydrates persisted state without replaying transitions. A cancelled slot carries its cancellation; any
     * other carries none.
     */
    public static Slot restore(SlotIdentity identity, SlotAssignment assignment, TimeWindow timeWindow,
                               Duration trackInterval, SlotStatus status, SlotCancellation cancellation) {
        return new Slot(identity, assignment, timeWindow, trackInterval, status, cancellation);
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

    /** Who cancelled the turn and why; empty unless it was cancelled. */
    public Optional<SlotCancellation> getCancellation() {
        return Optional.ofNullable(cancellation);
    }

    // Only the aggregate root may perform slot transitions.
    Slot start() {
        return transitionTo(SlotStatus.IN_PROGRESS, null);
    }

    Slot complete() {
        return transitionTo(SlotStatus.COMPLETED, null);
    }

    Slot cancel(SlotCancellation cancellation) {
        return transitionTo(SlotStatus.CANCELLED, Objects.requireNonNull(cancellation, "cancellation cannot be null"));
    }

    private Slot transitionTo(SlotStatus next, SlotCancellation cancellation) {
        boolean allowed = status == SlotStatus.SCHEDULED
                && (next == SlotStatus.IN_PROGRESS || next == SlotStatus.CANCELLED)
                || status == SlotStatus.IN_PROGRESS && next == SlotStatus.COMPLETED;
        if (!allowed) {
            throw new IllegalStateException("Cannot transition slot from " + status + " to " + next);
        }
        return new Slot(identity, new SlotAssignment(track, assignedJudges), timeWindow, trackInterval, next,
                cancellation);
    }

    public static Slot of(SlotIdentity identity, SlotAssignment assignment, TimeWindow timeWindow) {
        return new Slot(identity, assignment, timeWindow);
    }
}
