package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.support.TestValues;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each rule declares the source of what it reads and the named metrics it reads; a composite, those of its rules.
 */
class RuleSourcesTest {

    private static final BaseRule TIME = TimeBasedRule.standard(Points.of(100), 60.0);
    private static final BaseRule OBJECTIVES = ObjectivesRule.standard(Points.of(20));
    private static final BaseRule JUDGES = new JudgeSubjectiveRule("Evaluación de Jueces", 2.0);
    private static final Metric RESCUED = Metric.judged("victimas_rescatadas");
    private static final BaseRule VICTIMS = new VictimsRule("Víctimas rescatadas", RESCUED, Points.of(25));
    private static final DeductionRule ABANDONED =
            new AbandonedVictimsRule("Víctimas abandonadas", RESCUED, new VictimTariff(4, Points.of(10)));

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("rulesAndTheirSource")
    @DisplayName("Cada regla declara la fuente de lo que lee")
    void givenARuleThenItDeclaresTheSourceOfWhatItReads(ScoreRule rule, ResultSource expected) {
        assertThat(rule.source()).isEqualTo(expected);
    }

    static Stream<Arguments> rulesAndTheirSource() {
        ResultSource sensors = ResultSource.AUTOMATIC_MEASUREMENTS;
        ResultSource judges = ResultSource.JUDGE_PANEL;
        return Stream.of(
                Arguments.of(Named.of("Tiempo", TIME), sensors),
                Arguments.of(Named.of("Objetivos", OBJECTIVES), sensors),
                Arguments.of(Named.of("Faltas", new PenaltyRule("Faltas", Points.of(10))), sensors),
                Arguments.of(Named.of("Consumo", new ResourceConsumptionRule("Consumo", 100.0, 1.0)), sensors),
                Arguments.of(Named.of("Panel de jueces", JUDGES), judges),
                Arguments.of(Named.of("Salidas de línea", new CountedFaultRule("Salidas de línea",
                        Metric.sensor("salidas_de_linea"), new FaultTariff(1, Points.of(5)))), sensors),
                Arguments.of(Named.of("Precisión", new PrecisionRule("Precisión", Metric.sensor("precision"), Points.of(80))), sensors),
                Arguments.of(Named.of("Víctimas rescatadas", VICTIMS), judges),
                Arguments.of(Named.of("Víctimas abandonadas", ABANDONED), judges),
                Arguments.of(Named.of("Bonus por hito", new MilestoneBonusRule("Bonus por hito",
                        new Milestone(Metric.sensor("distancia_metros"), 10.0), Points.of(30))), sensors)
        );
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("rulesAndTheMetricsTheyRead")
    @DisplayName("Cada regla dice qué métricas con nombre lee; una compuesta, las de sus hijas")
    void givenARuleThenItNamesTheMetricsItReads(ScoreRule rule, Set<Metric> expected) {
        assertThat(rule.metrics()).isEqualTo(expected);
    }

    static Stream<Arguments> rulesAndTheMetricsTheyRead() {
        Metric precision = Metric.sensor("precision");
        Metric victimsBySensor = Metric.sensor("victimas_detectadas");
        return Stream.of(
                Arguments.of(Named.of("Tiempo", TIME), Set.of()),
                Arguments.of(Named.of("Panel de jueces", JUDGES), Set.of()),
                Arguments.of(Named.of("Precisión", new PrecisionRule("Precisión", precision, Points.of(80))), Set.of(precision)),
                Arguments.of(Named.of("Víctimas rescatadas", VICTIMS), Set.of(RESCUED)),
                Arguments.of(Named.of("Víctimas abandonadas", ABANDONED), Set.of(RESCUED)),
                Arguments.of(Named.of("Desempeño en pista", new CompositeScoreRule("Desempeño en pista",
                        List.of(TIME, OBJECTIVES))), Set.of()),
                Arguments.of(Named.of("Medido en pista", new CompositeScoreRule("Medido en pista", List.of(
                        new PrecisionRule("Precisión", precision, Points.of(80)),
                        new VictimsRule("Víctimas detectadas", victimsBySensor, Points.of(10))))),
                        Set.of(precision, victimsBySensor))
        );
    }

    @Test
    @DisplayName("\"Desempeño en pista\" suma Tiempo y Objetivos y conserva cada ítem")
    void givenTheTrackPerformanceCompositeThenItAddsTimeAndObjectivesKeepingEachItem() {
        CompositeScoreRule trackPerformance = new CompositeScoreRule("Desempeño en pista", List.of(TIME, OBJECTIVES));

        ScoreRule.RuleEvaluation evaluation = trackPerformance.evaluate(TestValues.metrics(50.0, 5, 0));

        assertThat(evaluation.items()).hasSize(2);
        assertThat(evaluation.total()).isEqualTo(Points.of(115 + 100));
        assertThat(trackPerformance.source()).isEqualTo(ResultSource.AUTOMATIC_MEASUREMENTS);
    }
}
