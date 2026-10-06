package com.roboleague.scheduling;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoundTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 9, 0);

    @Test
    void givenASlotOfTheRoundThenItIsFoundByItsId() {
        Round round = roundWith(slot("slot-1", Judge.of("j-1", "Juez Uno", "General")));

        assertThat(round.slot("slot-1")).map(Slot::getTeamId).contains("t-1");
        assertThat(round.slot("slot-9")).isEmpty();
    }

    @Test
    void givenASlotThenOnlyItsAssignedJudgesJudgeIt() {
        Slot slot = slot("slot-1", Judge.of("j-1", "Juez Uno", "General"));

        assertThat(slot.isJudgedBy("j-1")).isTrue();
        assertThat(slot.isJudgedBy("j-9")).isFalse();
    }

    private static Round roundWith(Slot slot) {
        Round round = Round.of(RoundInfo.of("r-1", "Ronda 1", RoundScope.of("ed-1", "cat-1", 1)));
        round.addSlot(slot);
        return round;
    }

    private static Slot slot(String slotId, Judge judge) {
        return Slot.of(SlotIdentity.of(slotId, "r-1", "t-1"),
                SlotAssignment.of(Track.active("trk-1", "Pista 1", "Madera"), List.of(judge)),
                new TimeWindow(START, START.plusMinutes(10)));
    }
}
