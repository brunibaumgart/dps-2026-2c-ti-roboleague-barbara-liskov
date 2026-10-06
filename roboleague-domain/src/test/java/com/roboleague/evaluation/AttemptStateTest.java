package com.roboleague.evaluation;

import com.roboleague.evaluation.Attempt.AttemptStatus;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class AttemptStateTest {

    private static final UnaryOperator<AttemptState> ONE_SOURCE_LEFT = state -> state.sourceReceived(false);
    private static final UnaryOperator<AttemptState> LAST_SOURCE = state -> state.sourceReceived(true);
    private static final UnaryOperator<AttemptState> APPEAL_FILED = AttemptState::appealFiled;
    private static final UnaryOperator<AttemptState> APPEAL_REJECTED = AttemptState::appealRejected;
    private static final UnaryOperator<AttemptState> APPEAL_ACCEPTED = AttemptState::appealAccepted;
    private static final UnaryOperator<AttemptState> FAULTS_ADJUSTED = AttemptState::faultsAdjusted;
    private static final UnaryOperator<AttemptState> DISQUALIFIED = AttemptState::disqualified;

    static Stream<Arguments> acceptedChanges() {
        return Stream.of(
                arguments(scheduled(), change("a source with another left", ONE_SOURCE_LEFT), AttemptStatus.AWAITING_SOURCES),
                arguments(scheduled(), change("the only source", LAST_SOURCE), AttemptStatus.EVALUATED),
                arguments(awaitingSources(), change("the last source", LAST_SOURCE), AttemptStatus.EVALUATED),
                arguments(evaluated(), change("appeal", APPEAL_FILED), AttemptStatus.UNDER_APPEAL),
                arguments(evaluated(), change("fault adjustment", FAULTS_ADJUSTED), AttemptStatus.ADJUSTED),
                arguments(evaluated(), change("disqualification", DISQUALIFIED), AttemptStatus.DISQUALIFIED),
                arguments(adjusted(), change("appeal", APPEAL_FILED), AttemptStatus.UNDER_APPEAL),
                arguments(adjusted(), change("fault adjustment", FAULTS_ADJUSTED), AttemptStatus.ADJUSTED),
                arguments(adjusted(), change("disqualification", DISQUALIFIED), AttemptStatus.DISQUALIFIED),
                arguments(underAppeal(), change("another appeal", APPEAL_FILED), AttemptStatus.UNDER_APPEAL),
                arguments(underAppeal(), change("rejection", APPEAL_REJECTED), AttemptStatus.EVALUATED),
                arguments(underAppeal(), change("acceptance", APPEAL_ACCEPTED), AttemptStatus.ADJUSTED));
    }

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @MethodSource("acceptedChanges")
    void givenAStateThenTheChangesItAcceptsLeadToTheNextOne(AttemptState state, UnaryOperator<AttemptState> change,
                                                            AttemptStatus next) {
        assertThat(change.apply(state).status()).isEqualTo(next);
    }

    static Stream<Arguments> refusedChanges() {
        return Stream.of(
                arguments(scheduled(), change("appeal", APPEAL_FILED), "attempt is scheduled: it cannot be appealed"),
                arguments(scheduled(), change("fault adjustment", FAULTS_ADJUSTED),
                        "attempt is scheduled: it cannot have its faults adjusted"),
                arguments(scheduled(), change("disqualification", DISQUALIFIED),
                        "attempt is scheduled: it cannot be disqualified"),
                arguments(evaluated(), change("another source", LAST_SOURCE),
                        "attempt is evaluated: it cannot receive results; "
                                + "corrections go through a fault adjustment or an appeal"),
                arguments(awaitingSources(), change("appeal", APPEAL_FILED),
                        "attempt is awaiting sources: it cannot be appealed"),
                arguments(disqualified(), change("a source", LAST_SOURCE),
                        "attempt is disqualified: it cannot receive results; "
                                + "corrections go through a fault adjustment or an appeal"),
                arguments(evaluated(), change("acceptance", APPEAL_ACCEPTED),
                        "attempt is evaluated: it cannot close an appeal: it has none open"),
                arguments(adjusted(), change("rejection", APPEAL_REJECTED),
                        "attempt is adjusted: it cannot close an appeal: it has none open"),
                arguments(underAppeal(), change("fault adjustment", FAULTS_ADJUSTED),
                        "attempt is under appeal: it cannot have its faults adjusted"),
                arguments(underAppeal(), change("disqualification", DISQUALIFIED),
                        "attempt is under appeal: it cannot be disqualified"),
                arguments(disqualified(), change("appeal", APPEAL_FILED), "attempt is disqualified: it cannot be appealed"),
                arguments(disqualified(), change("second disqualification", DISQUALIFIED),
                        "attempt is disqualified: it cannot be disqualified"));
    }

    @ParameterizedTest(name = "{0} + {1} is refused")
    @MethodSource("refusedChanges")
    void givenAStateThenTheChangesItDoesNotAcceptAreRefused(AttemptState state, UnaryOperator<AttemptState> change,
                                                            String message) {
        assertThatThrownBy(() -> change.apply(state))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(message);
    }

    @Test
    void givenTwoOpenAppealsThenClosingOneKeepsTheAttemptUnderAppealWithOneLeft() {
        AttemptState twoOpen = SettledAttemptState.evaluated().appealFiled().appealFiled();

        AttemptState oneLeft = twoOpen.appealRejected();

        assertThat(oneLeft.status()).isEqualTo(AttemptStatus.UNDER_APPEAL);
        assertThat(oneLeft.appealRejected().status()).isEqualTo(AttemptStatus.EVALUATED);
    }

    @Test
    void givenTwoOpenAppealsWhenOneIsAcceptedThenClosingTheOtherLeavesTheAttemptAdjusted() {
        AttemptState twoOpen = SettledAttemptState.evaluated().appealFiled().appealFiled();

        AttemptState afterBoth = twoOpen.appealAccepted().appealRejected();

        assertThat(afterBoth.status()).isEqualTo(AttemptStatus.ADJUSTED);
    }

    @Test
    void givenAnAdjustedAttemptThenARejectedAppealLeavesItAdjusted() {
        AttemptState settled = SettledAttemptState.adjusted().appealFiled().appealRejected();

        assertThat(settled.status()).isEqualTo(AttemptStatus.ADJUSTED);
    }

    private static Named<AttemptState> scheduled() {
        return Named.of("scheduled", WaitingAttemptState.scheduled());
    }

    private static Named<AttemptState> awaitingSources() {
        return Named.of("awaiting sources", WaitingAttemptState.awaitingSources());
    }

    private static Named<AttemptState> evaluated() {
        return Named.of("evaluated", SettledAttemptState.evaluated());
    }

    private static Named<AttemptState> adjusted() {
        return Named.of("adjusted", SettledAttemptState.adjusted());
    }

    private static Named<AttemptState> underAppeal() {
        return Named.of("under appeal", SettledAttemptState.evaluated().appealFiled());
    }

    private static Named<AttemptState> disqualified() {
        return Named.of("disqualified", new DisqualifiedAttemptState());
    }

    private static Named<UnaryOperator<AttemptState>> change(String name, UnaryOperator<AttemptState> change) {
        return Named.of(name, change);
    }
}
