package com.roboleague.scheduling;

import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable aggregate root controlling the schedule and lifecycle of its slots. */
public final class Round {
    public enum RoundStatus { SCHEDULED, IN_PROGRESS, COMPLETED }

    private final RoundInfo info;
    private final RoundStatus status;
    private final List<Slot> slots;

    public Round(RoundInfo info) {
        this(info, RoundStatus.SCHEDULED, List.of());
    }

    private Round(RoundInfo info, RoundStatus status, List<Slot> slots) {
        this.info = Objects.requireNonNull(info, "info cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.slots = List.copyOf(slots);
        for (int i = 0; i < this.slots.size(); i++) {
            Slot slot = this.slots.get(i);
            if (!slot.getRoundId().equals(getId())) {
                throw new IllegalArgumentException("Slot belongs to another round: " + slot.getSlotId());
            }
            for (Slot previous : this.slots.subList(0, i)) {
                if (previous.getSlotId().equals(slot.getSlotId()) || previous.getTeamId().equals(slot.getTeamId())) {
                    throw new IllegalArgumentException("Duplicate slot or team in round");
                }
                if (SlotConflicts.blockedUntil(slot, previous).isPresent()) {
                    throw new IllegalArgumentException("Overlapping slot resources");
                }
            }
            if (status == RoundStatus.SCHEDULED && slot.getStatus() != Slot.SlotStatus.SCHEDULED
                    && slot.getStatus() != Slot.SlotStatus.CANCELLED
                    || status == RoundStatus.COMPLETED && !terminal(slot)) {
                throw new IllegalArgumentException("Slot state is inconsistent with round state");
            }
        }
    }

    /** Restores the entire stored aggregate, preserving schedules and states. */
    public static Round restore(RoundInfo info, RoundStatus status, List<Slot> slots) {
        return new Round(info, status, slots);
    }

    public RoundInfo getInfo() { return info; }
    public RoundId getId() { return info.id(); }
    public String getName() { return info.name(); }
    public ChallengeId getChallengeId() { return info.scope().challengeId(); }
    public EditionId getEditionId() { return info.scope().editionId(); }
    public CategoryId getCategoryId() { return info.scope().categoryId(); }
    public int getRoundNumber() { return info.scope().roundNumber(); }
    public RoundStatus getStatus() { return status; }
    public List<Slot> getSlots() { return slots; }

    public Optional<Slot> slot(SlotId id) {
        return slots.stream().filter(slot -> slot.getSlotId().equals(id)).findFirst();
    }

    public Round addSlot(Slot slot) {
        requireStatus(RoundStatus.SCHEDULED);
        Objects.requireNonNull(slot, "slot cannot be null");
        if (slot.getStatus() != Slot.SlotStatus.SCHEDULED) {
            throw new IllegalArgumentException("New slots must be scheduled");
        }
        List<Slot> updated = new ArrayList<>(slots);
        updated.add(slot);
        return new Round(info, status, updated);
    }

    public Round start() {
        requireStatus(RoundStatus.SCHEDULED);
        return new Round(info, RoundStatus.IN_PROGRESS, slots);
    }

    public Round complete() {
        requireStatus(RoundStatus.IN_PROGRESS);
        if (!slots.stream().allMatch(Round::terminal)) {
            throw new IllegalStateException("All slots must be completed or cancelled");
        }
        return new Round(info, RoundStatus.COMPLETED, slots);
    }

    public Round startSlot(SlotId id) {
        requireStatus(RoundStatus.IN_PROGRESS);
        return transitionSlot(id, Slot.SlotStatus.IN_PROGRESS);
    }

    public Round completeSlot(SlotId id) {
        requireStatus(RoundStatus.IN_PROGRESS);
        return transitionSlot(id, Slot.SlotStatus.COMPLETED);
    }

    public Round cancelSlot(SlotId id) {
        if (status == RoundStatus.COMPLETED) {
            throw new IllegalStateException("Completed rounds cannot change");
        }
        return transitionSlot(id, Slot.SlotStatus.CANCELLED);
    }

    private Round transitionSlot(SlotId id, Slot.SlotStatus next) {
        Slot target = slot(id).orElseThrow(() -> new IllegalArgumentException("Slot not found: " + id));
        Slot updated = target.transitionTo(next);
        return new Round(info, status, slots.stream().map(slot -> slot == target ? updated : slot).toList());
    }

    private void requireStatus(RoundStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected round " + expected + ", was " + status);
        }
    }

    private static boolean terminal(Slot slot) {
        return slot.getStatus() == Slot.SlotStatus.COMPLETED || slot.getStatus() == Slot.SlotStatus.CANCELLED;
    }

    public static Round of(RoundInfo info) { return new Round(info); }
}
