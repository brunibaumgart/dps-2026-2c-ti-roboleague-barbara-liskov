package com.roboleague.scheduling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Competition round grouping slots for a category in an edition.
 */
public class Round {

    public enum RoundStatus {
        SCHEDULED,
        IN_PROGRESS,
        COMPLETED
    }

    private final RoundInfo info;
    private RoundStatus status;
    private final List<Slot> slots;

    public Round(RoundInfo info) {
        this.info = Objects.requireNonNull(info, "info cannot be null");
        this.status = RoundStatus.SCHEDULED;
        this.slots = new ArrayList<>();
    }

    public RoundInfo getInfo() {
        return info;
    }

    public String getId() {
        return info.id();
    }

    public String getName() {
        return info.name();
    }

    public String getEditionId() {
        return info.scope().editionId();
    }

    public String getCategoryId() {
        return info.scope().categoryId();
    }

    public int getRoundNumber() {
        return info.scope().roundNumber();
    }

    public RoundStatus getStatus() {
        return status;
    }

    public List<Slot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    public void addSlot(Slot slot) {
        Objects.requireNonNull(slot, "slot cannot be null");
        this.slots.add(slot);
    }

    public void start() {
        this.status = RoundStatus.IN_PROGRESS;
    }

    public void complete() {
        this.status = RoundStatus.COMPLETED;
    }

    public static Round of(RoundInfo info) {
        return new Round(info);
    }
}
