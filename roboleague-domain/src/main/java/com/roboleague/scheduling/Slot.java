package com.roboleague.scheduling;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Scheduled slot (turn) for a team on a specific track with assigned judges.
 */
public class Slot {

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
    private SlotStatus status;

    public Slot(SlotIdentity identity, SlotAssignment assignment, TimeWindow timeWindow) {
        this.identity = Objects.requireNonNull(identity, "identity cannot be null");
        Objects.requireNonNull(assignment, "assignment cannot be null");
        this.track = assignment.track();
        this.assignedJudges = new ArrayList<>(assignment.assignedJudges());
        this.timeWindow = Objects.requireNonNull(timeWindow, "timeWindow cannot be null");
        this.status = SlotStatus.SCHEDULED;
    }

    public SlotIdentity getIdentity() {
        return identity;
    }

    public String getSlotId() {
        return identity.slotId();
    }

    public String getRoundId() {
        return identity.roundId();
    }

    public String getTeamId() {
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
        return Collections.unmodifiableList(assignedJudges);
    }

    public SlotStatus getStatus() {
        return status;
    }

    public void start() {
        this.status = SlotStatus.IN_PROGRESS;
    }

    public void complete() {
        this.status = SlotStatus.COMPLETED;
    }

    public void cancel() {
        this.status = SlotStatus.CANCELLED;
    }

    public void assignJudge(Judge judge) {
        Objects.requireNonNull(judge, "judge cannot be null");
        if (!assignedJudges.contains(judge)) {
            assignedJudges.add(judge);
        }
    }

    public static Slot of(SlotIdentity identity, SlotAssignment assignment, TimeWindow timeWindow) {
        return new Slot(identity, assignment, timeWindow);
    }
}
