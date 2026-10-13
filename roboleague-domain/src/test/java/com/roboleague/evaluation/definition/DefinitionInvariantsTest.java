package com.roboleague.evaluation.definition;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * A definition that travels through the API and the database rejects what no rule or strategy could be built from,
 * with the reason in the message.
 */
class DefinitionInvariantsTest {

    private static final Parameters POINTS = Parameters.none().with("bonus", 12.5).with("whole", 3.0);

    static Stream<Arguments> rejections() {
        return Stream.of(
                rejected("parameter without a value", () -> Parameters.of(withNull("bonus")),
                        "parameter 'bonus' has no value"),
                rejected("parameter without a name", () -> Parameters.of(withNull(null)),
                        "parameter 'null' has no value"),
                rejected("missing parameter", () -> POINTS.number("maximum"), "missing parameter 'maximum'"),
                rejected("parameter that is not a number", () -> Parameters.none().with("bonus", Double.NaN).number("bonus"),
                        "parameter 'bonus' must be a finite number: NaN"),
                rejected("infinite parameter", () -> Parameters.none().with("bonus", Double.POSITIVE_INFINITY).number("bonus"),
                        "parameter 'bonus' must be a finite number: Infinity"),
                rejected("points with three decimals", () -> Parameters.none().with("bonus", 12.345).points("bonus"),
                        "parameter 'bonus' is an amount of points and takes at most 2 decimals: 12.345"),
                rejected("count with decimals", () -> POINTS.whole("bonus"),
                        "parameter 'bonus' must be a whole number: 12.5"),
                rejected("count beyond an int", () -> Parameters.none().with("whole", 1e10).whole("whole"),
                        "parameter 'whole' must be a whole number: 1.0E10"),
                rejected("missing metric", () -> RuleArguments.of(POINTS).metric("accuracy"),
                        "missing metric 'accuracy'"),
                rejected("rule without a type", () -> new RuleDefinition(null, "Tiempo", RuleArguments.of(POINTS)),
                        "a rule needs a type"),
                rejected("rule with a blank type", () -> new RuleDefinition(" ", "Tiempo", RuleArguments.of(POINTS)),
                        "a rule needs a type"),
                rejected("rule without a name", () -> new RuleDefinition("time", " ", RuleArguments.of(POINTS)),
                        "a rule of type 'time' needs a name"),
                rejected("strategy without a type", () -> new StrategyDefinition("", Parameters.none()),
                        "a strategy needs a type"),
                rejected("empty tie-break criterion", () -> new RulebookDefinition.Ranking(
                                new StrategyDefinition("all-rounds", Parameters.none()), Arrays.asList("higher-total", null)),
                        "a tie-break criterion cannot be empty"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejections")
    void givenADefinitionThatCannotBeBuiltThenItIsRejectedWithItsReason(ThrowingCallable creation,
                                                                        Class<? extends Throwable> type, String message) {
        assertThatThrownBy(creation).isInstanceOf(type).hasMessage(message);
    }

    private static Map<String, Double> withNull(String name) {
        Map<String, Double> values = new HashMap<>();
        values.put(name, name == null ? 1.0 : null);
        return values;
    }

    private static Arguments rejected(String name, ThrowingCallable creation, String message) {
        return arguments(Named.of(name, creation), IllegalArgumentException.class, message);
    }
}
