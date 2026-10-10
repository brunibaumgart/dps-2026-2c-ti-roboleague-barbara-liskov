package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.Slot;

import java.util.List;
import java.util.Objects;

/**
 * What the competition looks like at the moment of publishing: the table its results give now, the appeals still
 * open and the turns that have no outcome yet. {@link Standings#publish} decides with it.
 */
public record PublicationCheck(StandingsTable current, int openAppeals, int unfinishedTurns) {
    public PublicationCheck {
        Objects.requireNonNull(current, "current cannot be null");
        if (openAppeals < 0 || unfinishedTurns < 0) {
            throw new IllegalArgumentException("counts cannot be negative");
        }
    }

    /**
     * Counts open appeals over the attempts and, among the turns of the rounds that were not cancelled, those
     * whose attempt is missing or still awaits a source.
     */
    public static PublicationCheck of(StandingsTable current, List<Round> rounds, List<Attempt> attempts) {
        int openAppeals = 0;
        for (Attempt attempt : attempts) {
            openAppeals += attempt.openAppeals();
        }
        int unfinishedTurns = 0;
        for (Round round : rounds) {
            for (Slot slot : round.getSlots()) {
                if (slot.getStatus() != Slot.SlotStatus.CANCELLED && !hasOutcome(slot, attempts)) {
                    unfinishedTurns++;
                }
            }
        }
        return new PublicationCheck(current, openAppeals, unfinishedTurns);
    }

    private static boolean hasOutcome(Slot slot, List<Attempt> attempts) {
        return attempts.stream()
                .anyMatch(attempt -> attempt.getSlotId().equals(slot.getSlotId()) && attempt.hasOutcome());
    }
}
