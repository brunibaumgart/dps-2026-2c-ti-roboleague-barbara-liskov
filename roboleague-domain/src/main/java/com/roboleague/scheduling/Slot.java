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

    private final String slotId;
    private final String roundId;
    private final String teamId;
    private final Track track;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final List<Judge> assignedJudges;
    private SlotStatus status;

    public Slot(String slotId, String roundId, String teamId, Track track,
                LocalDateTime startTime, LocalDateTime endTime, List<Judge> assignedJudges) {
        this.slotId = Objects.requireNonNull(slotId, "slotId cannot be null");
        this.roundId = Objects.requireNonNull(roundId, "roundId cannot be null");
        this.teamId = Objects.requireNonNull(teamId, "teamId cannot be null");
        this.track = Objects.requireNonNull(track, "track cannot be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime cannot be null");
        this.endTime = Objects.requireNonNull(endTime, "endTime cannot be null");
        this.assignedJudges = assignedJudges != null ? new ArrayList<>(assignedJudges) : new ArrayList<>();
        this.status = SlotStatus.SCHEDULED;

        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime cannot be before startTime");
        }
    }

    public String getSlotId() {
        return slotId;
    }

    public String getRoundId() {
        return roundId;
    }

    public String getTeamId() {
        return teamId;
    }

    public Track getTrack() {
        return track;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
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
}
