package com.roboleague.evaluation.audit;

import com.roboleague.support.ActorId;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.roboleague.support.TestValues.TIME;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The audit of an attempt rejects records that could not be told apart or that describe no change.
 */
class AuditInvariantsTest {

    static Stream<Arguments> rejections() {
        return Stream.of(
                rejected("operation whose two events share an id", () -> new OperationAudit(TIME, "event-1", "event-1"),
                        "event ids must be nonblank and distinct"),
                rejected("operation with a blank event id", () -> new OperationAudit(TIME, " ", "event-2"),
                        "event ids must be nonblank and distinct"),
                rejected("snapshot of revision zero", () -> SnapshotIdentity.of("snapshot-1", 0),
                        "revisionNumber must be positive"),
                rejected("adjustment to revision zero", () -> ScoreAdjustmentDetails.of(0, "Corrección", ActorId.of("j-1")),
                        "newRevisionNumber must be positive"),
                rejected("penalty that adds no faults", () -> PenaltyDetail.of(0, "Choque"),
                        "additionalPenalties must be positive"),
                rejected("penalty that removes faults", () -> PenaltyDetail.of(-1, "Choque"),
                        "additionalPenalties must be positive"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejections")
    void givenARecordThatBreaksAnInvariantThenItIsRejectedWithItsReason(ThrowingCallable creation,
                                                                        Class<? extends Throwable> type, String message) {
        assertThatThrownBy(creation).isInstanceOf(type).hasMessage(message);
    }

    private static Arguments rejected(String name, ThrowingCallable creation, String message) {
        return arguments(Named.of(name, creation), IllegalArgumentException.class, message);
    }
}
