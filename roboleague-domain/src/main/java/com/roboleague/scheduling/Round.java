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

    private final String id;
    private final String editionId;
    private final String categoryId;
    private final int roundNumber;
    private final String name;
    private RoundStatus status;
    private final List<Slot> slots;

    public Round(String id, String editionId, String categoryId, int roundNumber, String name) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.editionId = Objects.requireNonNull(editionId, "editionId cannot be null");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId cannot be null");
        this.roundNumber = roundNumber;
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.status = RoundStatus.SCHEDULED;
        this.slots = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getEditionId() {
        return editionId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public String getName() {
        return name;
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
}
