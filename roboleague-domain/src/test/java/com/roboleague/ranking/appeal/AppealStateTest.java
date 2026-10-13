package com.roboleague.ranking.appeal;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.support.ActorId;
import com.roboleague.support.TestValues;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.roboleague.support.TestValues.TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Transition table of an appeal: what each state accepts and what it refuses, with the reason.
 */
class AppealStateTest {

    private static final ActorId REVIEWER = ActorId.of("arb-1");
    private static final Consumer<Appeal> REVIEW = appeal -> appeal.beginReview(ActorId.of("arb-2"));
    private static final Consumer<Appeal> ACCEPT =
            appeal -> appeal.accept("Cronómetro defectuoso", TestValues.metrics(42.0, 1, 0), REVIEWER, TIME);
    private static final Consumer<Appeal> ACCEPT_WITHOUT_METRICS =
            appeal -> appeal.accept("Cronómetro defectuoso", null, REVIEWER, TIME);
    private static final Consumer<Appeal> REJECT = appeal -> appeal.reject("La falta se cometió", REVIEWER, TIME);

    static Stream<Arguments> acceptedChanges() {
        return Stream.of(
                arguments(pending(), change("review", REVIEW), "UNDER_REVIEW"),
                arguments(underReview(), change("acceptance", ACCEPT), "ACCEPTED"),
                arguments(underReview(), change("rejection", REJECT), "REJECTED"));
    }

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @MethodSource("acceptedChanges")
    void givenAStateThenTheChangesItAcceptsLeadToTheNextOne(Supplier<Appeal> state, Consumer<Appeal> change,
                                                            String next) {
        Appeal appeal = state.get();

        change.accept(appeal);

        assertThat(appeal.getStatusName()).isEqualTo(next);
    }

    static Stream<Arguments> refusedChanges() {
        return Stream.of(
                arguments(pending(), change("acceptance", ACCEPT), IllegalStateException.class,
                        "Appeal must be placed under review before being accepted"),
                arguments(pending(), change("rejection", REJECT), IllegalStateException.class,
                        "Appeal must be placed under review before being rejected"),
                arguments(underReview(), change("another review", REVIEW), IllegalStateException.class,
                        "Appeal is already under review by arb-1"),
                arguments(underReview(), change("acceptance without revised metrics", ACCEPT_WITHOUT_METRICS),
                        IllegalArgumentException.class, "Revised metrics are mandatory when accepting an appeal"),
                arguments(accepted(), change("review", REVIEW), IllegalStateException.class,
                        "Cannot re-review an already accepted appeal"),
                arguments(accepted(), change("second acceptance", ACCEPT), IllegalStateException.class,
                        "Appeal has already been accepted"),
                arguments(accepted(), change("rejection", REJECT), IllegalStateException.class,
                        "Cannot reject an already accepted appeal"),
                arguments(rejected(), change("review", REVIEW), IllegalStateException.class,
                        "Cannot review an already rejected appeal"),
                arguments(rejected(), change("acceptance", ACCEPT), IllegalStateException.class,
                        "Cannot accept an already rejected appeal"),
                arguments(rejected(), change("second rejection", REJECT), IllegalStateException.class,
                        "Appeal has already been rejected"));
    }

    @ParameterizedTest(name = "{0} + {1} is refused")
    @MethodSource("refusedChanges")
    void givenAStateThenTheChangesItDoesNotAcceptAreRefusedAndTheStateStays(Supplier<Appeal> state,
                                                                            Consumer<Appeal> change,
                                                                            Class<? extends Throwable> type,
                                                                            String message) {
        Appeal appeal = state.get();
        String before = appeal.getStatusName();

        assertThatThrownBy(() -> change.accept(appeal)).isInstanceOf(type).hasMessage(message);
        assertThat(appeal.getStatusName()).isEqualTo(before);
    }

    @Test
    void givenABlankReasonThenTheAppealIsRejected() {
        assertThatThrownBy(() -> new AppealClaim(" ", "Video"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("an appeal says what it claims: reason cannot be blank");
    }

    private static Named<Supplier<Appeal>> pending() {
        return Named.of("pending", AppealStateTest::filed);
    }

    private static Named<Supplier<Appeal>> underReview() {
        return Named.of("under review", () -> {
            Appeal appeal = filed();
            appeal.beginReview(REVIEWER);
            return appeal;
        });
    }

    private static Named<Supplier<Appeal>> accepted() {
        return Named.of("accepted", () -> {
            Appeal appeal = underReview().getPayload().get();
            ACCEPT.accept(appeal);
            return appeal;
        });
    }

    private static Named<Supplier<Appeal>> rejected() {
        return Named.of("rejected", () -> {
            Appeal appeal = underReview().getPayload().get();
            REJECT.accept(appeal);
            return appeal;
        });
    }

    private static Appeal filed() {
        return Appeal.of(AppealId.of("app-1"), AttemptId.parse("slot-1-1"), TeamId.of("team-1"),
                "Error en la medición de tiempo", "Video de la cámara 2", TIME);
    }

    private static Named<Consumer<Appeal>> change(String name, Consumer<Appeal> change) {
        return Named.of(name, change);
    }
}
