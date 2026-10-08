package com.roboleague.scheduling;

import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void lifecycleChangesOnlyThroughTheRoundAndLeavesOriginalSnapshotsUnchanged() {
        Round original = roundWith(slot("slot-1", Judge.of(JudgeId.of("j-1"), "Judge", "General")));
        Round started = original.start();
        Round running = started.startSlot(SlotId.of("slot-1"));
        Round done = running.completeSlot(SlotId.of("slot-1")).complete();
        assertThat(original.getStatus()).isEqualTo(Round.RoundStatus.SCHEDULED);
        assertThat(original.getSlots().getFirst().getStatus()).isEqualTo(Slot.SlotStatus.SCHEDULED);
        assertThat(started.getSlots().getFirst().getStatus()).isEqualTo(Slot.SlotStatus.SCHEDULED);
        assertThat(running.getSlots().getFirst().getStatus()).isEqualTo(Slot.SlotStatus.IN_PROGRESS);
        assertThat(done.getStatus()).isEqualTo(Round.RoundStatus.COMPLETED);
        assertThat(done.getSlots().getFirst().getStatus()).isEqualTo(Slot.SlotStatus.COMPLETED);
        assertThatThrownBy(done::start).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(done::complete).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> done.cancelSlot(SlotId.of("slot-1"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsSkippingOrRepeatingTransitionsAndUnknownSlots() {
        Round round = roundWith(slot("slot-1", Judge.of(JudgeId.of("j-1"), "Judge", "General")));
        assertThatThrownBy(round::complete).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> round.startSlot(SlotId.of("slot-1"))).isInstanceOf(IllegalStateException.class);
        Round started = round.start();
        assertThatThrownBy(started::start).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(started::complete).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> started.completeSlot(SlotId.of("slot-1"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> started.startSlot(SlotId.of("missing"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> started.addSlot(round.getSlots().getFirst())).isInstanceOf(IllegalStateException.class);
        Round running = started.startSlot(SlotId.of("slot-1"));
        assertThatThrownBy(() -> running.startSlot(SlotId.of("slot-1"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> running.cancelSlot(SlotId.of("slot-1"))).isInstanceOf(IllegalStateException.class);
        Round completedSlot = running.completeSlot(SlotId.of("slot-1"));
        assertThatThrownBy(() -> completedSlot.completeSlot(SlotId.of("slot-1"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void scheduledSlotsCanBeCancelledAndTerminalSlotsAllowRoundCompletion() {
        Round original = roundWith(slot("slot-1", Judge.of(JudgeId.of("j-1"), "Judge", "General")));
        Round cancelled = original.cancelSlot(SlotId.of("slot-1"));
        assertThatThrownBy(() -> cancelled.cancelSlot(SlotId.of("slot-1"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cancelled.start().startSlot(SlotId.of("slot-1"))).isInstanceOf(IllegalStateException.class);
        assertThat(cancelled.start().complete().getStatus()).isEqualTo(Round.RoundStatus.COMPLETED);
    }

    @Test
    void rejectsForeignSlotsDuplicateIdentitiesAndDuplicateTeamsWithoutChangingTheRound() {
        Round round = roundWith(slot("slot-1", Judge.of(JudgeId.of("j-1"), "Judge", "General")));
        SlotAssignment assignment = new SlotAssignment(Track.active(TrackId.of("trk-2"), "Track", "Wood"),
                        List.of(Judge.of(JudgeId.of("j-2"), "Judge", "General")));
        TimeWindow window = new TimeWindow(START.plusMinutes(10), START.plusMinutes(20));
        assertThatThrownBy(() -> round.addSlot(Slot.of(new SlotIdentity(SlotId.of("slot-2"), RoundId.of("foreign"), TeamId.of("t-2")), assignment, window)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> round.addSlot(Slot.of(new SlotIdentity(SlotId.of("slot-1"), round.getId(), TeamId.of("t-2")), assignment, window)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> round.addSlot(Slot.of(new SlotIdentity(SlotId.of("slot-2"), round.getId(), TeamId.of("t-1")), assignment, window)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(round.getSlots()).hasSize(1);
    }

    @Test
    void rejectsTrackAndJudgeOverlapsButAllowsHalfOpenBoundaries() {
        Round round = roundWith(slot("slot-1", Judge.of(JudgeId.of("j-1"), "Judge", "General")));
        SlotIdentity identity = new SlotIdentity(SlotId.of("slot-2"), round.getId(), TeamId.of("t-2"));
        TimeWindow overlap = new TimeWindow(START.plusMinutes(5), START.plusMinutes(15));
        assertThatThrownBy(() -> round.addSlot(Slot.of(identity,
                new SlotAssignment(Track.active(TrackId.of("trk-1"), "Other name", "Wood"),
                        List.of(Judge.of(JudgeId.of("j-2"), "Judge", "General"))), overlap)))
                .isInstanceOf(IllegalArgumentException.class);
        SlotAssignment sameJudge = new SlotAssignment(Track.active(TrackId.of("trk-2"), "Track", "Wood"),
                List.of(Judge.of(JudgeId.of("j-1"), "Other profile", "Other")));
        assertThatThrownBy(() -> round.addSlot(Slot.of(identity, sameJudge, overlap)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(round.addSlot(Slot.of(identity, sameJudge,
                new TimeWindow(START.plusMinutes(10), START.plusMinutes(20)))).getSlots()).hasSize(2);
    }

    @Test
    void exposesImmutableCollectionsAndRestoresWithoutReplayingLifecycle() {
        Round completed = roundWith(slot("slot-1", Judge.of(JudgeId.of("j-1"), "Judge", "General")))
                .start().startSlot(SlotId.of("slot-1")).completeSlot(SlotId.of("slot-1")).complete();
        Slot original = completed.getSlots().getFirst();
        Slot restoredSlot = Slot.restore(original.getIdentity(), new SlotAssignment(original.getTrack(), original.getAssignedJudges()),
                original.getTimeWindow(), original.getTrackInterval(), original.getStatus());
        var slots = new java.util.ArrayList<>(List.of(restoredSlot));
        Round restored = Round.restore(completed.getInfo(), completed.getStatus(), slots);
        slots.clear();
        assertThat(restored.getSlots()).hasSize(1);
        assertThat(restored.getStatus()).isEqualTo(Round.RoundStatus.COMPLETED);
        assertThat(restored.getSlots().getFirst().getStatus()).isEqualTo(Slot.SlotStatus.COMPLETED);
        assertThatThrownBy(() -> restored.getSlots().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restoredSlot.getAssignedJudges().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> Round.restore(completed.getInfo(), Round.RoundStatus.SCHEDULED, List.of(restoredSlot)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Round roundWith(Slot slot) {
        Round round = Round.of(RoundInfo.of(RoundId.of("r-1"), "Ronda 1", RoundScope.of(com.roboleague.tournament.ChallengeId.of("ch-maze"), EditionId.of("ed-1"), CategoryId.of("cat-1"), 1)));
        round = round.addSlot(slot);
        return round;
    }

    private static Slot slot(String slotId, Judge judge) {
        return Slot.of(SlotIdentity.of(SlotId.of(slotId), RoundId.of("r-1"), TeamId.of("t-1")),
                SlotAssignment.of(Track.active(TrackId.of("trk-1"), "Pista 1", "Madera"), List.of(judge)),
                new TimeWindow(START, START.plusMinutes(10)));
    }
}
