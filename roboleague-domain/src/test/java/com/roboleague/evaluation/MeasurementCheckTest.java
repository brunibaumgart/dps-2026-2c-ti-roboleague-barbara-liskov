package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MeasurementCheckTest {

    private static final ResultSource SENSORS = ResultSource.AUTOMATIC_MEASUREMENTS;
    private static final ResultSource JUDGES = ResultSource.JUDGE_PANEL;

    private final Rulebook rescue = new Rulebook(RulebookVersion.first(), new ScoringScheme(
            new MetricSheet(List.of(
                    new MetricDefinition(Metric.sensor("colisiones"), MeasurementUnit.COUNT, ValueRange.atLeast(0.0)),
                    new MetricDefinition(Metric.sensor("precision"), MeasurementUnit.RATIO, ValueRange.between(0.0, 1.0)),
                    new MetricDefinition(Metric.judged("victimas"), MeasurementUnit.COUNT, ValueRange.between(0.0, 4.0)))),
            new ScoreRules(List.of(new PenaltyRule("Faltas", 10.0)), List.of()), new Unlimited()),
            new RankingScheme(new AllRounds(), List.of(new HigherTotal())));

    @Test
    @DisplayName("Lo que manda una fuente se acepta si trae cada métrica declarada para ella, en su unidad y rango")
    void givenEveryDeclaredMetricInRangeThenTheMeasurementsAreAccepted() {
        assertThat(rescue.check(SENSORS, Map.of("colisiones", 2.0, "precision", 0.75)))
                .isEqualTo(new MeasurementCheck.Accepted());
    }

    @Test
    @DisplayName("Cada fuente se revisa sola: el panel no tiene que traer las mediciones de los sensores (F3)")
    void givenOneSourceThenOnlyItsDeclaredMetricsAreRequired() {
        assertThat(rescue.check(JUDGES, Map.of("victimas", 3.0))).isEqualTo(new MeasurementCheck.Accepted());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("measurementsWithProblems")
    @DisplayName("Lo que no cumple el reglamento se rechaza con todos los problemas juntos")
    void givenMeasurementsThatBreakTheRulebookThenEveryProblemIsReported(String caseName, ResultSource source,
                                                                          Map<String, Double> measurements,
                                                                          List<String> problems) {
        assertThat(rescue.check(source, measurements)).isEqualTo(new MeasurementCheck.Rejected(problems));
    }

    static Stream<Arguments> measurementsWithProblems() {
        return Stream.of(
                Arguments.of("falta una métrica", SENSORS, Map.of("colisiones", 2.0),
                        List.of("measurement 'precision' is missing")),
                Arguments.of("métrica mal escrita", SENSORS, Map.of("colisiones", 2.0, "precisoin", 0.75),
                        List.of("measurement 'precision' is missing",
                                "measurement 'precisoin' is not declared for AUTOMATIC_MEASUREMENTS")),
                Arguments.of("métrica de la otra fuente", JUDGES, Map.of("victimas", 3.0, "colisiones", 2.0),
                        List.of("measurement 'colisiones' is not declared for JUDGE_PANEL")),
                Arguments.of("fuera de rango y no entera", SENSORS, Map.of("colisiones", 2.5, "precision", 1.4),
                        List.of("measurement 'colisiones' is not a valid COUNT: 2.5",
                                "measurement 'precision' must be between 0.0 and 1.0: 1.4")),
                Arguments.of("más víctimas que las del desafío", JUDGES, Map.of("victimas", 5.0),
                        List.of("measurement 'victimas' must be between 0.0 and 4.0: 5.0")));
    }
}
