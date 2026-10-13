package com.roboleague.scheduling;

import com.roboleague.support.ActorId;
import com.roboleague.support.TestValues;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Each value and transition of scheduling rejects what would break its invariant, with the reason in the message.
 */
class SchedulingInvariantsTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 9, 0);
    private static final RoundId ROUND = RoundId.of("r-1");
    private static final Judge JUDGE = Judge.of(JudgeId.of("j-1"), "Juez Uno", "General");
    private static final Track TRACK = Track.active(TrackId.of("trk-1"), "Pista 1", "Madera");
    private static final Track INACTIVE = Track.of(TrackInfo.of(TrackId.of("trk-9"), "Pista cerrada", "Madera"), false);
    private static final SlotAssignment ASSIGNMENT = SlotAssignment.of(TRACK, List.of(JUDGE));
    private static final TimeWindow TEN_MINUTES = TimeWindow.of(START, START.plusMinutes(10));
    private static final TimeWindow NEXT_TEN_MINUTES = TimeWindow.of(START.plusMinutes(10), START.plusMinutes(20));
    private static final SlotCancellation NO_SHOW =
            new SlotCancellation(ActorId.of("org-1"), "El equipo no se presentó", START);

    static Stream<Arguments> rejections() {
        return Stream.of(
                invalid("window ending before it starts", () -> TimeWindow.of(START, START.minusMinutes(1)),
                        "endTime cannot be before startTime"),
                invalid("assignment to an inactive track", () -> SlotAssignment.of(INACTIVE, List.of(JUDGE)),
                        "Slot requires an active track"),
                invalid("assignment without judges", () -> SlotAssignment.of(TRACK, List.of()),
                        "Slot requires distinct assigned judges"),
                invalid("assignment with the same judge twice", () -> SlotAssignment.of(TRACK,
                                List.of(JUDGE, Judge.of(JudgeId.of("j-1"), "Otro perfil", "Otra"))),
                        "Slot requires distinct assigned judges"),
                invalid("slot lasting zero minutes", () -> Slot.of(identity("slot-1", "t-1"), ASSIGNMENT,
                                TimeWindow.of(START, START)),
                        "Slot duration must be positive and track interval nonnegative"),
                invalid("slot with a negative track pause", () -> new Slot(identity("slot-1", "t-1"), ASSIGNMENT,
                                TEN_MINUTES, Duration.ofMinutes(-1)),
                        "Slot duration must be positive and track interval nonnegative"),
                invalid("cancelled slot without its cancellation", () -> Slot.restore(identity("slot-1", "t-1"),
                                ASSIGNMENT, TEN_MINUTES, Duration.ZERO, Slot.SlotStatus.CANCELLED, null),
                        "Exactly the cancelled slots record who cancelled them and why"),
                invalid("scheduled slot with a cancellation", () -> Slot.restore(identity("slot-1", "t-1"),
                                ASSIGNMENT, TEN_MINUTES, Duration.ZERO, Slot.SlotStatus.SCHEDULED, NO_SHOW),
                        "Exactly the cancelled slots record who cancelled them and why"),
                invalid("cancellation without a reason", () -> new SlotCancellation(ActorId.of("org-1"), " ", START),
                        "a cancellation needs a reason"),
                invalid("round number zero", () -> scope(0), "roundNumber must be positive"),
                invalid("negative round number", () -> scope(-1), "roundNumber must be positive"),
                invalid("resources with a repeated track", () -> RoundResources.of(List.of(TRACK, TRACK), List.of(JUDGE)),
                        "Resource ids must be unique"),
                invalid("resources with a repeated judge", () -> RoundResources.of(List.of(TRACK), List.of(JUDGE, JUDGE)),
                        "Resource ids must be unique"),
                invalid("resources with only inactive tracks", () -> RoundResources.of(List.of(INACTIVE), List.of(JUDGE)),
                        "At least one active track and one judge must be available"),
                invalid("resources without judges", () -> RoundResources.of(List.of(TRACK), List.of()),
                        "At least one active track and one judge must be available"),
                invalid("timing with zero-length slots", () -> RoundScheduleTiming.of(START, Duration.ZERO, Duration.ZERO),
                        "slotDuration must be positive"),
                invalid("timing with a negative pause",
                        () -> RoundScheduleTiming.of(START, Duration.ofMinutes(10), Duration.ofMinutes(-1)),
                        "intervalBetweenSlots cannot be negative"),
                invalid("scheduling the same team twice", () -> new RoundSchedulerService(TestValues.ids()).scheduleRound(
                                RoundScheduleRequest.of(info(), RoundResources.of(List.of(TRACK), List.of(JUDGE)),
                                        RoundScheduleTiming.of(START, Duration.ofMinutes(10), Duration.ZERO)),
                                List.of(TeamId.of("t-1"), TeamId.of("t-1"))),
                        "Team ids must be unique within a round"),
                invalid("adding a slot of another round", () -> round().addSlot(Slot.of(
                                SlotIdentity.of(SlotId.of("slot-2"), RoundId.of("foreign"), TeamId.of("t-2")),
                                ASSIGNMENT, NEXT_TEN_MINUTES)),
                        "Slot belongs to another round: slot-2"),
                invalid("adding the same team twice", () -> round().addSlot(Slot.of(identity("slot-2", "t-1"),
                                ASSIGNMENT, NEXT_TEN_MINUTES)),
                        "Duplicate slot or team in round"),
                invalid("adding a slot that overlaps on the same track", () -> round().addSlot(Slot.of(
                                identity("slot-2", "t-2"), SlotAssignment.of(TRACK,
                                        List.of(Judge.of(JudgeId.of("j-2"), "Juez Dos", "General"))),
                                TimeWindow.of(START.plusMinutes(5), START.plusMinutes(15)))),
                        "Overlapping slot resources"),
                invalid("adding a slot that is not scheduled", () -> Round.of(info()).addSlot(Slot.restore(
                                identity("slot-1", "t-1"), ASSIGNMENT, TEN_MINUTES, Duration.ZERO,
                                Slot.SlotStatus.CANCELLED, NO_SHOW)),
                        "New slots must be scheduled"),
                invalid("restoring a scheduled round with a slot in progress", () -> Round.restore(info(),
                                Round.RoundStatus.SCHEDULED, List.of(Slot.restore(identity("slot-1", "t-1"), ASSIGNMENT,
                                        TEN_MINUTES, Duration.ZERO, Slot.SlotStatus.IN_PROGRESS, null))),
                        "Slot state is inconsistent with round state"),
                invalid("starting a slot that is not in the round", () -> round().start().startSlot(SlotId.of("slot-9")),
                        "Slot not found: slot-9"),
                refused("adding a slot to a started round", () -> round().start().addSlot(Slot.of(
                                identity("slot-2", "t-2"), ASSIGNMENT, NEXT_TEN_MINUTES)),
                        "Expected round SCHEDULED, was IN_PROGRESS"),
                refused("completing a round with a slot pending", () -> round().start().complete(),
                        "All slots must be completed or cancelled"),
                refused("cancelling a slot of a completed round", () -> completedRound().cancelSlot(SlotId.of("slot-1"), NO_SHOW),
                        "Completed rounds cannot change"),
                refused("completing a slot that has not started", () -> round().start().completeSlot(SlotId.of("slot-1")),
                        "Cannot transition slot from SCHEDULED to COMPLETED"),
                refused("cancelling a slot in progress",
                        () -> round().start().startSlot(SlotId.of("slot-1")).cancelSlot(SlotId.of("slot-1"), NO_SHOW),
                        "Cannot transition slot from IN_PROGRESS to CANCELLED"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejections")
    void givenAChangeThatBreaksAnInvariantThenItIsRejectedWithItsReason(ThrowingCallable change,
                                                                        Class<? extends Throwable> type, String message) {
        assertThatThrownBy(change).isInstanceOf(type).hasMessage(message);
    }

    private static Round round() {
        return Round.of(info()).addSlot(Slot.of(identity("slot-1", "t-1"), ASSIGNMENT, TEN_MINUTES));
    }

    private static Round completedRound() {
        return round().start().startSlot(SlotId.of("slot-1")).completeSlot(SlotId.of("slot-1")).complete();
    }

    private static RoundInfo info() {
        return RoundInfo.of(ROUND, "Ronda 1", scope(1));
    }

    private static RoundScope scope(int number) {
        return RoundScope.of(ChallengeId.of("ch-maze"), EditionId.of("ed-1"), CategoryId.of("cat-1"), number);
    }

    private static SlotIdentity identity(String slotId, String teamId) {
        return SlotIdentity.of(SlotId.of(slotId), ROUND, TeamId.of(teamId));
    }

    private static Arguments invalid(String name, ThrowingCallable change, String message) {
        return arguments(Named.of(name, change), IllegalArgumentException.class, message);
    }

    private static Arguments refused(String name, ThrowingCallable change, String message) {
        return arguments(Named.of(name, change), IllegalStateException.class, message);
    }
}
