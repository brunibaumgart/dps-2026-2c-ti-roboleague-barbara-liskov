package com.roboleague.tournament;

import com.roboleague.support.ActorId;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.roboleague.support.TestValues.DATE;
import static com.roboleague.support.TestValues.TIME;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Each value of the tournament rejects the data that would break its invariant, with the reason in the message.
 */
class TournamentInvariantsTest {

    static Stream<Arguments> rejections() {
        return Stream.of(
                rejected("team size with no members", () -> new TeamSizeRange(0, 3),
                        "minMembers must be positive"),
                rejected("team size with a negative minimum", () -> new TeamSizeRange(-1, 3),
                        "minMembers must be positive"),
                rejected("team size with maximum under minimum", () -> new TeamSizeRange(3, 2),
                        "maxMembers cannot be less than minMembers"),
                rejected("age range with a negative minimum", () -> new AgeRange(-1, 18),
                        "Invalid age limits: minAge=-1, maxAge=18"),
                rejected("age range with maximum under minimum", () -> new AgeRange(18, 17),
                        "Invalid age limits: minAge=18, maxAge=17"),
                rejected("dates ending before they start", () -> DateRange.of(DATE, DATE.minusDays(1)),
                        "endDate cannot be before startDate"),
                rejected("season before 2000", () -> new Season("s-1", 1999, "1999"),
                        "Invalid season year: 1999"),
                rejected("season after 2100", () -> new Season("s-1", 2101, "2101"),
                        "Invalid season year: 2101"),
                rejected("robot with negative actuators", () -> RobotHardware.of(-1, Set.of()),
                        "actuatorCount cannot be negative"),
                rejected("verified documents with a revocation reason",
                        () -> Documentation.restore(Map.of(), true, TIME, ActorId.of("inspector"), "Falta consentimiento"),
                        "Verified documents cannot have a revocation reason"),
                rejected("unverified documents with a verification date",
                        () -> Documentation.restore(Map.of(), false, TIME, null, null),
                        "Unverified documents cannot have verification metadata"),
                rejected("unverified documents with a verifier",
                        () -> Documentation.restore(Map.of(), false, null, ActorId.of("inspector"), null),
                        "Unverified documents cannot have verification metadata"),
                rejected("edition number zero", () -> EditionHeader.of(EditionId.of("ed-1"), "Edición", 0),
                        "editionNumber must be positive"),
                rejected("negative edition number", () -> EditionHeader.of(EditionId.of("ed-1"), "Edición", -1),
                        "editionNumber must be positive"),
                rejected("weight zero", () -> new Weight(0), "grams must be positive"),
                rejected("negative weight", () -> new Weight(-1), "grams must be positive"),
                rejected("length zero", () -> new Dimensions(0, 100, 100), "All dimensions must be positive"),
                rejected("negative width", () -> new Dimensions(100, -1, 100), "All dimensions must be positive"),
                rejected("height zero", () -> new Dimensions(100, 100, 0), "All dimensions must be positive"),
                rejected("member born after the reference date",
                        () -> TeamMember.of(ParticipantId.of("m-1"), "Ana", DATE.plusDays(1), "MEMBER", DATE),
                        "birthDate cannot be in the future"),
                rejected("team update with another identity",
                        () -> team().withProfile(TeamProfile.of(TeamId.of("team-2"), "Otro", "Escuela")),
                        "A team update cannot change its identity"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejections")
    void givenDataThatBreaksAnInvariantThenItIsRejectedWithItsReason(ThrowingCallable creation,
                                                                     Class<? extends Throwable> type, String message) {
        assertThatThrownBy(creation).isInstanceOf(type).hasMessage(message);
    }

    private static Team team() {
        TeamMember member = TeamMember.of(ParticipantId.of("m-1"), "Ana", DATE.minusYears(16), "MEMBER", DATE);
        Robot robot = new Robot(RobotId.of("robot-1"), "Robot", RobotSpecification.of(1000, 100, 100, 100, 1, Set.of()));
        return Team.of(TeamId.of("team-1"), "Equipo", "Escuela", robot, List.of(member), new Documentation());
    }

    private static Arguments rejected(String name, ThrowingCallable creation, String message) {
        return arguments(Named.of(name, creation), IllegalArgumentException.class, message);
    }
}
