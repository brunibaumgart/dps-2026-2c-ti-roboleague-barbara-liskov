package com.roboleague.evaluation;

import com.roboleague.evaluation.Attempt.AttemptStatus;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.SlotId;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Each value of the evaluation rejects what would break its invariant, with the reason in the message.
 */
class EvaluationInvariantsTest {

    private static final TrackPerformance TRACK = new TrackPerformance(50.0, 3, 0);
    private static final Metric RESCUED = Metric.judged("victimas_rescatadas");

    static Stream<Arguments> rejections() {
        return Stream.of(
                invalid("time that is not a number", () -> new TrackPerformance(Double.NaN, 3, 0),
                        "timeTakenSeconds must be a finite number: NaN"),
                invalid("infinite time", () -> new TrackPerformance(Double.POSITIVE_INFINITY, 3, 0),
                        "timeTakenSeconds must be a finite number: Infinity"),
                invalid("negative time", () -> new TrackPerformance(-1.0, 3, 0),
                        "timeTakenSeconds cannot be negative"),
                invalid("negative objectives", () -> new TrackPerformance(50.0, -1, 0),
                        "objectivesCompleted cannot be negative"),
                invalid("negative penalties", () -> new TrackPerformance(50.0, 3, -1),
                        "penaltiesCount cannot be negative"),
                invalid("negative consumption", () -> new Measurements(TRACK, -0.5, Map.of()),
                        "consumption must be a finite, non-negative number: -0.5"),
                invalid("consumption that is not a number", () -> new Measurements(TRACK, Double.NaN, Map.of()),
                        "consumption must be a finite, non-negative number: NaN"),
                invalid("sensor measurement without a value", () -> new Measurements(TRACK, 0.0, withNull("distancia")),
                        "measurement 'distancia' has no value"),
                invalid("judge panel without scores", () -> new JudgeScores(Map.of(), Map.of(RESCUED.name(), 2.0)),
                        "a judge panel report needs at least one judge score"),
                invalid("judged measurement without a value",
                        () -> new JudgeScores(Map.of(JudgeId.of("j-1"), 8.0), withNull(RESCUED.name())),
                        "measurement 'victimas_rescatadas' has no value"),
                invalid("judge score that is not a number",
                        () -> new JudgeScores(Map.of(JudgeId.of("j-1"), Double.NaN), Map.of()),
                        "score of judge 'j-1' must be a finite, non-negative number: NaN"),
                invalid("range whose minimum is not a number", () -> new ValueRange(Double.NaN, 4.0),
                        "min must be a finite number: NaN"),
                invalid("range whose minimum is infinite", () -> ValueRange.atLeast(Double.NEGATIVE_INFINITY),
                        "min must be a finite number: -Infinity"),
                invalid("range whose maximum is not a number", () -> new ValueRange(0.0, Double.NaN),
                        "max cannot be less than min: NaN < 0.0"),
                invalid("closed range with an infinite maximum", () -> ValueRange.between(0.0, Double.POSITIVE_INFINITY),
                        "max must be a finite number: Infinity"),
                invalid("negative bonus cap", () -> new CappedAt(Points.of(-10)),
                        "maximum must be a finite, non-negative number: -10.00"),
                invalid("attempt number zero", () -> AttemptId.of(SlotId.of("slot-1"), 0),
                        "attempt number must be positive: 0"),
                invalid("rulebook version zero", () -> new RulebookVersion(0), "number must be positive"),
                invalid("scoring without rules", () -> new ScoreRules(List.of(), List.of(), List.of()),
                        "a scoring scheme needs at least one rule"),
                invalid("attempt under appeal without open appeals",
                        () -> new AttemptStage(AttemptStatus.UNDER_APPEAL, 0, AttemptStatus.EVALUATED).state(),
                        "an attempt under appeal has at least one appeal open: 0"),
                invalid("measurement that is not a number", () -> judged(Double.NaN).measurement(RESCUED),
                        "measurement 'victimas_rescatadas' must be a finite number: NaN"),
                invalid("count that is not whole", () -> judged(2.5).count(RESCUED),
                        "measurement 'victimas_rescatadas' must be a whole count: 2.5"),
                invalid("negative count", () -> judged(-1.0).count(RESCUED),
                        "measurement 'victimas_rescatadas' must be a whole count: -1.0"),
                refused("measurement that was not captured", () -> RawMetrics.nothingMeasured().measurement(RESCUED),
                        "measurement 'victimas_rescatadas' was not captured"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejections")
    void givenAValueThatBreaksAnInvariantThenItIsRejectedWithItsReason(ThrowingCallable creation,
                                                                       Class<? extends Throwable> type, String message) {
        assertThatThrownBy(creation).isInstanceOf(type).hasMessage(message);
    }

    private static RawMetrics judged(double value) {
        return RawMetrics.of(TRACK, EvaluationFeedback.of(0.0, Map.of(), Map.of(RESCUED.name(), value)));
    }

    private static Map<String, Double> withNull(String name) {
        Map<String, Double> values = new HashMap<>();
        values.put(name, null);
        return values;
    }

    private static Arguments invalid(String name, ThrowingCallable creation, String message) {
        return arguments(Named.of(name, creation), IllegalArgumentException.class, message);
    }

    private static Arguments refused(String name, ThrowingCallable creation, String message) {
        return arguments(Named.of(name, creation), IllegalStateException.class, message);
    }
}
