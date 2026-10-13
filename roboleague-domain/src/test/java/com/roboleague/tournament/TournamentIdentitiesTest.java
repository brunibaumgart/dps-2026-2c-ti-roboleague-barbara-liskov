package com.roboleague.tournament;

import com.roboleague.support.IdentityContract;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Identities of the tournament follow the identity contract.
 */
class TournamentIdentitiesTest {

    static Stream<Function<String, Object>> identities() {
        return Stream.of(TeamId::of, EditionId::of, CategoryId::of, ParticipantId::of, RobotId::of, ChallengeId::of);
    }

    @ParameterizedTest
    @MethodSource("identities")
    void rejectsMissingValuesAndPreservesHistoricalText(Function<String, Object> identity) {
        IdentityContract.rejectsMissingValuesAndPreservesHistoricalText(identity);
    }

    @ParameterizedTest
    @MethodSource("identities")
    void acceptsUpToTheStoredColumnLengthAndRejectsLongerValues(Function<String, Object> identity) {
        IdentityContract.acceptsUpToTheStoredColumnLength(identity);
    }
}
