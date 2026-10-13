package com.roboleague.support;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Function;
import java.util.stream.Stream;

/**
 * An actor id follows the identity contract.
 */
class ActorIdTest {

    static Stream<Function<String, Object>> identities() {
        return Stream.of(ActorId::of);
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
