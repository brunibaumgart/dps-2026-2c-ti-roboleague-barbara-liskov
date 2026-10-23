package com.roboleague.ranking;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.CategoryId;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static com.roboleague.support.TestValues.TIME;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Each value of the standings rejects what would break its invariant, with the reason in the message.
 */
class RankingInvariantsTest {

    private static final StandingsTable EMPTY = new StandingsTable(RulebookVersion.first(), List.of());
    private static final StandingsId ID = StandingsId.of(StandingsFixtures.CHALLENGE, CategoryId.of("cat-1"));
    private static final AuditNote NOTE = AuditNote.of(ActorId.of("org-1"), "Publicación oficial");
    private static final AttemptId ATTEMPT = AttemptId.of(SlotId.of("slot-1"), 1);
    private static final Rulebook SECOND_VERSION = new Rulebook(new RulebookVersion(2),
            StandingsFixtures.RULEBOOK.scoring(), StandingsFixtures.RULEBOOK.rankingScheme());

    static Stream<Arguments> rejections() {
        return Stream.of(
                invalid("position zero", () -> new Placement(0, "Primero"), "position must be >= 1: 0"),
                invalid("version zero", () -> new StandingsVersion(0, TIME, EMPTY), "standings versions start at 1: 0"),
                invalid("publication of version zero", () -> new OfficialPublication(0, TIME, NOTE),
                        "standings versions start at 1: 0"),
                invalid("negative open appeals", () -> new PublicationCheck(EMPTY, -1, 0), "counts cannot be negative"),
                invalid("negative unfinished turns", () -> new PublicationCheck(EMPTY, 0, -1), "counts cannot be negative"),
                invalid("blocked publication without reasons", () -> new StandingsPublication.Blocked(List.of()),
                        "a blocked publication says why"),
                invalid("attempt of another team", () -> new TeamAttempts(StandingsFixtures.team("t-2"),
                                List.of(StandingsFixtures.opened("t-1", "r-1", 1))),
                        "attempt slot-r-1-t-1-1 belongs to t-1, not to t-2"),
                missing("round result without round", () -> new RoundResult(null, ATTEMPT, Points.ZERO, List.of()),
                        "roundId cannot be null"),
                missing("round result without attempt", () -> new RoundResult(RoundId.of("r-1"), null, Points.ZERO, List.of()),
                        "attemptId cannot be null"),
                missing("round result without total", () -> new RoundResult(RoundId.of("r-1"), ATTEMPT, null, List.of()),
                        "total cannot be null"),
                missing("round result without other attempts", () -> new RoundResult(RoundId.of("r-1"), ATTEMPT,
                        Points.ZERO, null), "otherAttempts cannot be null"),
                invalid("round result whose counted attempt is also another", () -> new RoundResult(RoundId.of("r-1"),
                                ATTEMPT, Points.ZERO, List.of(ATTEMPT)),
                        "attempt slot-1-1 cannot be both the one that counted and another"),
                missing("table without rulebook version", () -> new StandingsTable(null, List.of()),
                        "rulebookVersion cannot be null"),
                missing("team rounds without attempt rule", () -> new TeamRounds(List.of(), List.of(), "todas", null),
                        "attemptRule cannot be null"),
                invalid("results scored with another version", () -> new CategoryResults(SECOND_VERSION,
                                List.of(new TeamAttempts(StandingsFixtures.team("t-1"),
                                        List.of(StandingsFixtures.opened("t-1", "r-1", 1)))), List.of()),
                        "attempt slot-r-1-t-1-1 was scored with ch-maze v1, not with v2"),
                corrupt("stored standings without versions", () -> Standings.restore(ID, List.of(), List.of()),
                        "stored standings have at least one version: " + ID),
                corrupt("stored standings skipping a version", () -> Standings.restore(ID,
                                List.of(version(1), version(3)), List.of()),
                        "stored standings " + ID + " skip version 2"),
                corrupt("stored publication of a version that does not exist", () -> Standings.restore(ID,
                                List.of(version(1)), List.of(new OfficialPublication(2, TIME, NOTE))),
                        "stored standings " + ID + " publish v2 out of order"),
                corrupt("stored publications out of order", () -> Standings.restore(ID, List.of(version(1), version(2)),
                                List.of(new OfficialPublication(2, TIME, NOTE), new OfficialPublication(1, TIME, NOTE))),
                        "stored standings " + ID + " publish v1 out of order"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejections")
    void givenAValueThatBreaksAnInvariantThenItIsRejectedWithItsReason(ThrowingCallable creation,
                                                                       Class<? extends Throwable> type, String message) {
        assertThatThrownBy(creation).isInstanceOf(type).hasMessage(message);
    }

    private static StandingsVersion version(int number) {
        return new StandingsVersion(number, TIME, EMPTY);
    }

    private static Arguments invalid(String name, ThrowingCallable creation, String message) {
        return arguments(Named.of(name, creation), IllegalArgumentException.class, message);
    }

    private static Arguments missing(String name, ThrowingCallable creation, String message) {
        return arguments(Named.of(name, creation), NullPointerException.class, message);
    }

    private static Arguments corrupt(String name, ThrowingCallable creation, String message) {
        return arguments(Named.of(name, creation), IllegalStateException.class, message);
    }
}
