package com.roboleague.scheduling;

import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoundTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 9, 0);

    @Test
    void givenASlotOfTheRoundThenItIsFoundByItsId() {
        Round round = roundWith(slot("slot-1", Judge.of(JudgeId.of("j-1"), "Juez Uno", "General")));

        assertThat(round.slot(SlotId.of("slot-1"))).map(Slot::getTeamId).contains(TeamId.of("t-1"));
        assertThat(round.slot(SlotId.of("slot-9"))).isEmpty();
    }

    @Test
    void givenASlotThenOnlyItsAssignedJudgesJudgeIt() {
        Slot slot = slot("slot-1", Judge.of(JudgeId.of("j-1"), "Juez Uno", "General"));

        assertThat(slot.isJudgedBy(JudgeId.of("j-1"))).isTrue();
        assertThat(slot.isJudgedBy(JudgeId.of("j-9"))).isFalse();
    }

    private static Round roundWith(Slot slot) {
        Round round = Round.of(RoundInfo.of(RoundId.of("r-1"), "Ronda 1", RoundScope.of(EditionId.of("ed-1"), CategoryId.of("cat-1"), 1)));
        round.addSlot(slot);
        return round;
    }

    private static Slot slot(String slotId, Judge judge) {
        return Slot.of(SlotIdentity.of(SlotId.of(slotId), RoundId.of("r-1"), TeamId.of("t-1")),
                SlotAssignment.of(Track.active(TrackId.of("trk-1"), "Pista 1", "Madera"), List.of(judge)),
                new TimeWindow(START, START.plusMinutes(10)));
    }
}
