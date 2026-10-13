package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.support.TestValues;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * A rule rejects parameters that would turn a deduction into a bonus, a bonus into a deduction or a formula into
 * nonsense, and measurements its formula cannot score, with the reason in the message.
 */
class RuleInvariantsTest {

    private static final List<String> MUTATOR_PREFIXES = List.of("add", "remove", "set", "clear", "replace");
    private static final Metric PRECISION = Metric.sensor("precision");
    private static final Metric RESCUED = Metric.judged("victimas_rescatadas");
    private static final Metric DISTANCE = Metric.sensor("distancia_metros");

    static Stream<Arguments> rejections() {
        return Stream.of(
                rejected("objectives with negative points", () -> new ObjectivesRule("Objetivos", Points.of(-1)),
                        "pointsPerObjective cannot be negative"),
                rejected("all objectives out of zero", () -> new AllObjectivesBonusRule("Todos", 0, Points.of(25)),
                        "totalObjectives must be positive"),
                rejected("all objectives with a negative bonus", () -> new AllObjectivesBonusRule("Todos", 5, Points.of(-25)),
                        "bonus cannot be negative"),
                rejected("penalty with a negative deduction", () -> new PenaltyRule("Penalizaciones", Points.of(-10)),
                        "deductionPerPenalty cannot be negative: a penalty never adds points"),
                rejected("consumption with a negative maximum", () -> new ResourceConsumptionRule("Consumo", -1.0, 2.0),
                        "maxAllowedConsumption cannot be negative"),
                rejected("consumption with a negative penalty", () -> new ResourceConsumptionRule("Consumo", 100.0, -2.0),
                        "penaltyPerExcessUnit cannot be negative: excess consumption never adds points"),
                rejected("judges with a negative weight", () -> new JudgeSubjectiveRule("Jueces", -1.0),
                        "weightMultiplier cannot be negative: a judge score never subtracts points"),
                rejected("time with a negative base", () -> TimeTargets.of(Points.of(-100), 60.0),
                        "basePoints cannot be negative"),
                rejected("time with a target of zero seconds", () -> TimeTargets.of(Points.of(100), 0.0),
                        "targetTimeSeconds must be positive"),
                rejected("time with a negative bonus per second", () -> TimeAdjustments.of(-1.5, 2.0, Points.ZERO),
                        "pointsPerSecondUnder cannot be negative"),
                rejected("time with a negative deduction per second", () -> TimeAdjustments.of(1.5, -2.0, Points.ZERO),
                        "deductionPerSecondOver cannot be negative: being slower never adds points"),
                rejected("time with a negative minimum", () -> TimeAdjustments.of(1.5, 2.0, Points.of(-5)),
                        "minPoints cannot be negative"),
                rejected("time whose minimum exceeds its base",
                        () -> TimeRuleConfig.of(Points.of(100), 60.0, 1.5, 2.0, Points.of(120)),
                        "minPoints cannot exceed basePoints: 120.00 > 100.00"),
                rejected("counted faults with a negative allowance", () -> new FaultTariff(-1, Points.of(5)),
                        "freeAllowance cannot be negative"),
                rejected("counted faults with a negative deduction", () -> new FaultTariff(1, Points.of(-5)),
                        "deductionPerFault cannot be negative: a fault never adds points"),
                rejected("precision with a negative maximum", () -> new PrecisionRule("Precisión", PRECISION, Points.of(-80)),
                        "maxPoints cannot be negative"),
                rejected("precision measured above one", () -> new PrecisionRule("Precisión", PRECISION, Points.of(80))
                                .evaluate(measured(PRECISION, 1.5)),
                        "precision must be between 0 and 1: 1.5"),
                rejected("victims with negative points", () -> new VictimsRule("Víctimas", RESCUED, Points.of(-25)),
                        "pointsPerRescued cannot be negative"),
                rejected("victims out of zero", () -> new VictimTariff(0, Points.of(10)),
                        "totalVictims must be positive"),
                rejected("abandoned victims with a negative deduction", () -> new VictimTariff(4, Points.of(-10)),
                        "deductionPerAbandoned cannot be negative: leaving a victim never adds points"),
                rejected("more rescued victims than there are", () -> new AbandonedVictimsRule("Abandonadas", RESCUED,
                                new VictimTariff(4, Points.of(10))).evaluate(measured(RESCUED, 5.0)),
                        "rescued victims cannot exceed 4: 5"),
                rejected("milestone with a negative bonus", () -> new MilestoneBonusRule("Hito",
                                new Milestone(DISTANCE, 10.0), Points.of(-30)),
                        "bonus cannot be negative"),
                rejected("milestone with a negative threshold", () -> new Milestone(DISTANCE, -1.0),
                        "threshold must be a finite, non-negative number: -1.0"),
                rejected("milestone whose threshold is not a number", () -> new Milestone(DISTANCE, Double.NaN),
                        "threshold must be a finite, non-negative number: NaN"),
                rejected("milestone that can never be reached", () -> new Milestone(DISTANCE, Double.POSITIVE_INFINITY),
                        "threshold must be a finite, non-negative number: Infinity"),
                rejected("composite without rules", () -> new CompositeScoreRule("Vacía", List.of()),
                        "a composite rule needs at least one rule"),
                rejected("composite mixing sources", () -> new CompositeScoreRule("Mezcla",
                                List.of(TimeBasedRule.standard(Points.of(100), 60.0), new JudgeSubjectiveRule("Jueces", 1.0))),
                        "a composite rule needs rules from exactly one source: [AUTOMATIC_MEASUREMENTS, JUDGE_PANEL]"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejections")
    void givenParametersOrMeasurementsTheRuleCannotScoreThenTheyAreRejectedWithTheReason(
            ThrowingCallable creation, Class<? extends Throwable> type, String message) {
        assertThatThrownBy(creation).isInstanceOf(type).hasMessage(message);
    }

    @Test
    @DisplayName("La regla compuesta del reglamento no expone operaciones para cambiar sus reglas")
    void givenTheCompositeRuleThenItHasNoPublicMutators() {
        List<String> mutators = Arrays.stream(CompositeScoreRule.class.getMethods())
                .filter(method -> method.getDeclaringClass() == CompositeScoreRule.class)
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .map(Method::getName)
                .filter(name -> MUTATOR_PREFIXES.stream().anyMatch(name::startsWith))
                .toList();

        assertThat(mutators).isEmpty();
    }

    @Test
    @DisplayName("La regla compuesta copia sus reglas; cambiar la lista recibida no la modifica")
    void givenTheCompositeRuleThenItsRulesListIsUnmodifiable() {
        List<BaseRule> received = new ArrayList<>(List.of(TimeBasedRule.standard(Points.of(100), 60.0)));
        CompositeScoreRule composite = new CompositeScoreRule("Laberinto", received);

        received.add(ObjectivesRule.standard(Points.of(15)));

        assertThat(composite.definition().arguments().rules()).hasSize(1);
    }

    private static RawMetrics measured(Metric metric, double value) {
        return RawMetrics.of(new TrackPerformance(50.0, 0, 0), TestValues.measured(Map.of(metric.name(), value)));
    }

    private static Arguments rejected(String name, ThrowingCallable creation, String message) {
        return arguments(Named.of(name, creation), IllegalArgumentException.class, message);
    }
}
