package com.roboleague.scheduling;

import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static com.roboleague.support.TestValues.ids;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundSchedulerServiceTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 8, 10, 0);
    private final RoundSchedulerService scheduler = new RoundSchedulerService(ids());

    @Test
    void twoTracksAndTwoJudgesScheduleInParallelWithTrackRecovery() {
        Round round = scheduler.scheduleRound(request("r", 2, 2, 1), teams("A", "B", "C", "D"));
        assertThat(round.getSlots()).extracting(Slot::getStartTime)
                .containsExactly(START, START, START.plusMinutes(6), START.plusMinutes(6));
        assertThat(round.getSlots()).extracting(Slot::getEndTime)
                .containsExactly(START.plusMinutes(5), START.plusMinutes(5), START.plusMinutes(11), START.plusMinutes(11));
        assertThat(round.getSlots()).extracting(slot -> slot.getTrack().id().value())
                .containsExactly("p1", "p2", "p1", "p2");
        assertThat(round.getSlots()).extracting(slot -> slot.getAssignedJudges().getFirst().id().value())
                .containsExactly("j1", "j2", "j1", "j2");
        assertThat(round.getSlots()).allSatisfy(slot -> assertThat(slot.getAssignedJudges()).hasSize(1));
        assertThat(round.getSlots()).extracting(slot -> slot.getSlotId().value())
                .containsExactly("generated-1", "generated-2", "generated-3", "generated-4");
    }

    @Test
    void oneJudgeCanSwitchTracksAtThePreviousSlotsEnd() {
        Round round = scheduler.scheduleRound(request("r", 2, 1, 1), teams("A", "B", "C"));
        assertThat(round.getSlots()).extracting(Slot::getStartTime)
                .containsExactly(START, START.plusMinutes(5), START.plusMinutes(10));
        assertThat(round.getSlots()).extracting(slot -> slot.getTrack().id().value())
                .containsExactly("p1", "p2", "p1");
    }

    @Test
    void oneTrackHasRecoveryEvenWhenAnotherJudgeIsFree() {
        Round round = scheduler.scheduleRound(request("r", 1, 2, 1), teams("A", "B"));
        assertThat(round.getSlots()).extracting(Slot::getStartTime)
                .containsExactly(START, START.plusMinutes(6));
    }

    @Test
    void zeroRecoveryAllowsAdjacentSlotsOnTheSameTrack() {
        Round round = scheduler.scheduleRound(request("r", 1, 1, 0), teams("A", "B"));
        assertThat(round.getSlots().get(1).getStartTime()).isEqualTo(START.plusMinutes(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"track", "judge", "team"})
    void storedOccupationsAreMatchedByIdentityAcrossRequests(String resource) {
        Slot old = new Slot(new SlotIdentity(SlotId.of("old-slot"), RoundId.of("old"), TeamId.of("A")),
                new SlotAssignment(track("p1"), List.of(judge("j1"))),
                new TimeWindow(START, START.plusMinutes(5)), Duration.ofMinutes(2));
        Round existing = Round.of(info("old")).addSlot(old);
        Track track = track(resource.equals("track") ? "p1" : "different-track");
        Judge judge = judge(resource.equals("judge") ? "j1" : "different-judge");
        RoundScheduleRequest request = new RoundScheduleRequest(info("new"), new RoundResources(List.of(track), List.of(judge)),
                new RoundScheduleTiming(START, Duration.ofMinutes(5), Duration.ZERO));
        Round round = scheduler.scheduleRound(request, teams(resource.equals("team") ? "A" : "B"), List.of(existing));
        assertThat(round.getSlots().getFirst().getStartTime())
                .isEqualTo(START.plusMinutes(resource.equals("track") ? 7 : 5));
        assertThat(existing.getSlots().getFirst().getStartTime()).isEqualTo(START);
    }

    @Test
    void advancesThroughFutureConflictsAndPreservesBothTrackPauses() {
        Slot first = new Slot(new SlotIdentity(SlotId.of("old-1"), RoundId.of("old"), TeamId.of("X")),
                new SlotAssignment(track("p1"), List.of(judge("j2"))),
                new TimeWindow(START.plusMinutes(4), START.plusMinutes(8)), Duration.ofMinutes(2));
        Slot second = new Slot(new SlotIdentity(SlotId.of("old-2"), RoundId.of("old"), TeamId.of("Y")),
                new SlotAssignment(track("p2"), List.of(judge("j1"))),
                new TimeWindow(START.plusMinutes(12), START.plusMinutes(17)));
        Round existing = Round.of(info("old")).addSlot(first).addSlot(second);
        Round result = scheduler.scheduleRound(request("new", 1, 1, 1), teams("A"), List.of(existing));
        assertThat(result.getSlots().getFirst().getStartTime()).isEqualTo(START.plusMinutes(17));
    }

    @Test
    void canFillAnEarlierGapWhenItsTrackPauseEndsAtTheNextOccupation() {
        Slot old = new Slot(new SlotIdentity(SlotId.of("old-slot"), RoundId.of("old"), TeamId.of("X")),
                new SlotAssignment(track("p1"), List.of(judge("j1"))),
                new TimeWindow(START.plusMinutes(6), START.plusMinutes(11)));
        Round result = scheduler.scheduleRound(request("new", 1, 1, 1), teams("A"),
                List.of(Round.of(info("old")).addSlot(old)));
        assertThat(result.getSlots().getFirst().getStartTime()).isEqualTo(START);
    }

    @Test
    void cancellationReleasesResourcesForAnotherRound() {
        Round old = scheduler.scheduleRound(request("old", 1, 1, 1), teams("A"));
        old = old.cancelSlot(old.getSlots().getFirst().getSlotId());
        Round result = scheduler.scheduleRound(request("new", 1, 1, 1), teams("A"), List.of(old));
        assertThat(result.getSlots().getFirst().getStartTime()).isEqualTo(START);
    }

    @Test
    void ignoresInactiveTracksAndKeepsActiveResourceOrder() {
        RoundScheduleRequest request = new RoundScheduleRequest(info("r"), new RoundResources(
                List.of(Track.of(track("off").info(), false), track("p2"), track("p1")), List.of(judge("j1"))),
                new RoundScheduleTiming(START, Duration.ofMinutes(5), Duration.ZERO));
        Round result = scheduler.scheduleRound(request, teams("A"));
        assertThat(result.getSlots().getFirst().getTrack().id()).isEqualTo(TrackId.of("p2"));
    }

    @Test
    void rejectsDuplicateResourceIdsEvenWhenProfilesDiffer() {
        assertThatThrownBy(() -> new RoundResources(List.of(track("p1"), Track.active(TrackId.of("p1"), "Other", "Metal")),
                List.of(judge("j1")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoundResources(List.of(track("p1")),
                List.of(judge("j1"), Judge.of(JudgeId.of("j1"), "Other", "Other"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnavailableResourcesAndInvalidTimingOrTeams() {
        assertThatThrownBy(() -> new RoundResources(List.of(Track.of(track("off").info(), false)), List.of(judge("j1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoundResources(List.of(track("p1")), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoundResources(List.of(), List.of(judge("j1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoundScheduleTiming(START, Duration.ZERO, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoundScheduleTiming(START, Duration.ofMinutes(-1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoundScheduleTiming(START, Duration.ofMinutes(1), Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scheduler.scheduleRound(request("r", 1, 1, 1), teams("A", "A")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RoundScheduleRequest request(String id, int tracks, int judges, int interval) {
        return new RoundScheduleRequest(info(id), new RoundResources(
                tracks == 1 ? List.of(track("p1")) : List.of(track("p1"), track("p2")),
                judges == 1 ? List.of(judge("j1")) : List.of(judge("j1"), judge("j2"))),
                new RoundScheduleTiming(START, Duration.ofMinutes(5), Duration.ofMinutes(interval)));
    }
    private static RoundInfo info(String id) {
        return new RoundInfo(RoundId.of(id), id,
                new RoundScope(ChallengeId.of("challenge"), EditionId.of("edition"), CategoryId.of("category"), 1));
    }
    private static Track track(String id) { return Track.active(TrackId.of(id), id, "Wood"); }
    private static Judge judge(String id) { return Judge.of(JudgeId.of(id), id, "General"); }
    private static List<TeamId> teams(String... names) {
        return java.util.Arrays.stream(names).map(TeamId::of).toList();
    }
}
