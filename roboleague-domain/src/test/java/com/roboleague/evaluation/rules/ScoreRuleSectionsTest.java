package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.EvaluationFeedback;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.scheduling.JudgeId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The contract of each section, over runs from perfect to poor: a base rule or a bonus never subtracts and a
 * deduction never adds. A new rule type gets a case here in its section.
 */
class ScoreRuleSectionsTest {

    private static final Metric COLLISIONS = Metric.sensor("colisiones");
    private static final Metric PRECISION = Metric.sensor("precision");
    private static final Metric RESCUED = Metric.judged("rescatadas");
    private static final Metric CHECKPOINT = Metric.sensor("checkpoint");

    private static List<Named<RawMetrics>> runs() {
        return List.of(
                Named.of("carrera perfecta", run(new TrackPerformance(30.0, 5, 0), 50.0, 10.0,
                        Map.of(COLLISIONS.name(), 0.0, PRECISION.name(), 1.0, RESCUED.name(), 4.0, CHECKPOINT.name(), 1.0))),
                Named.of("carrera media", run(new TrackPerformance(60.0, 3, 2), 80.0, 5.0,
                        Map.of(COLLISIONS.name(), 2.0, PRECISION.name(), 0.5, RESCUED.name(), 2.0, CHECKPOINT.name(), 1.0))),
                Named.of("carrera mala", run(new TrackPerformance(200.0, 0, 6), 150.0, 0.0,
                        Map.of(COLLISIONS.name(), 9.0, PRECISION.name(), 0.0, RESCUED.name(), 0.0, CHECKPOINT.name(), 0.0))));
    }

    private static RawMetrics run(TrackPerformance performance, double consumption, double judgeScore,
                                  Map<String, Double> measurements) {
        return new RawMetrics(performance, EvaluationFeedback.of(consumption, Map.of(JudgeId.of("j1"), judgeScore), measurements));
    }

    private static Stream<Arguments> inEveryRun(List<? extends Named<? extends ScoreRule>> rules) {
        return rules.stream().flatMap(rule -> runs().stream().map(run -> Arguments.of(rule, run)));
    }

    static Stream<Arguments> baseRules() {
        TimeBasedRule time = TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.5, 2.0, 0.0);
        ObjectivesRule objectives = new ObjectivesRule("Objetivos", 20.0);
        return inEveryRun(List.of(
                Named.of("tiempo", time),
                Named.of("objetivos", objectives),
                Named.of("panel de jueces", new JudgeSubjectiveRule("Jueces", 5.0)),
                Named.of("precisión", new PrecisionRule("Precisión", PRECISION, 50.0)),
                Named.of("víctimas", new VictimsRule("Víctimas", RESCUED, 25.0)),
                Named.of("compuesta", new CompositeScoreRule("Desempeño en pista", List.of(time, objectives)))));
    }

    static Stream<Arguments> bonusRules() {
        return inEveryRun(List.of(
                Named.of("hito", new MilestoneBonusRule("Checkpoint", new Milestone(CHECKPOINT, 1.0), 30.0)),
                Named.of("todos los objetivos", new AllObjectivesBonusRule("Todos los objetivos", 5, 25.0))));
    }

    static Stream<Arguments> deductionRules() {
        return inEveryRun(List.of(
                Named.of("faltas", new PenaltyRule("Faltas", 15.0)),
                Named.of("faltas contadas", new CountedFaultRule("Colisiones", COLLISIONS, new FaultTariff(1, 5.0))),
                Named.of("consumo", new ResourceConsumptionRule("Consumo", 80.0, 0.5)),
                Named.of("víctimas abandonadas", new AbandonedVictimsRule("Víctimas abandonadas", RESCUED,
                        new VictimTariff(4, 10.0)))));
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("baseRules")
    @DisplayName("Una regla base nunca resta")
    void givenABaseRuleThenItNeverSubtracts(BaseRule rule, RawMetrics run) {
        assertThat(rule.evaluate(run).total()).isGreaterThanOrEqualTo(0.0);
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("bonusRules")
    @DisplayName("Una bonificación nunca resta")
    void givenABonusRuleThenItNeverSubtracts(BonusRule rule, RawMetrics run) {
        assertThat(rule.evaluate(run).total()).isGreaterThanOrEqualTo(0.0);
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("deductionRules")
    @DisplayName("Una deducción nunca suma")
    void givenADeductionRuleThenItNeverAdds(DeductionRule rule, RawMetrics run) {
        assertThat(rule.evaluate(run).total()).isLessThanOrEqualTo(0.0);
    }
}
