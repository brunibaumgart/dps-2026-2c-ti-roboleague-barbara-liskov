package com.roboleague.api.graphql.view;

import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.Slot;

import java.util.Comparator;
import java.util.List;

/**
 * GraphQL view of a round with its slots, in the same order as REST (start time, track, slot id).
 * {@code challenge} and {@code category} are resolved in batch from the ids kept here.
 */
public record RoundView(String id, int number, String name, String status, String challengeId, String editionId,
                        String categoryId, List<SlotView> slots) {

    public static RoundView from(Round round) {
        return new RoundView(round.getId().value(), round.getRoundNumber(), round.getName(), round.getStatus().name(),
                round.getChallengeId().value(), round.getEditionId().value(), round.getCategoryId().value(),
                round.getSlots().stream()
                        .sorted(Comparator.comparing(Slot::getStartTime)
                                .thenComparing(slot -> slot.getTrack().id().value())
                                .thenComparing(slot -> slot.getSlotId().value()))
                        .map(slot -> SlotView.from(round, slot))
                        .toList());
    }
}
