package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.EvaluationFeedback;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.TrackPerformance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeasuredRulesTest {

    private static final Metric LINE_EXITS = Metric.sensor("salidas_de_linea");
    private static final Metric PRECISION = Metric.sensor("precision");
    private static final Metric RESCUED = Metric.judged("victimas_rescatadas");
    private static final Metric DISTANCE = Metric.sensor("distancia_metros");

    private static RawMetrics measuring(Metric metric, double value) {
        return new RawMetrics(new TrackPerformance(60.0, 0, 0), EvaluationFeedback.withMeasurements(Map.of(metric.name(), value)));
    }

    private static double subtotal(ScoreRule rule, RawMetrics metrics) {
        return rule.evaluate(metrics).total();
    }

    @ParameterizedTest(name = "{0} salidas con {1} sin cargo a {2} pts → {3}")
    @CsvSource({"0, 2, 5.0, 0.0", "2, 2, 5.0, 0.0", "5, 2, 5.0, -15.0", "3, 0, 10.0, -30.0"})
    @DisplayName("Las primeras faltas toleradas no descuentan; cada una extra descuenta la tarifa")
    void givenCountedFaultsThenOnlyThoseBeyondTheAllowanceAreDeducted(int exits, int free, double deduction,
                                                                     double expected) {
        CountedFaultRule lineExits = new CountedFaultRule("Salidas de línea", LINE_EXITS, new FaultTariff(free, deduction));

        assertThat(subtotal(lineExits, measuring(LINE_EXITS, exits))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "precisión {0} → {1} pts")
    @CsvSource({"0.0, 0.0", "0.5, 40.0", "1.0, 80.0"})
    @DisplayName("La precisión escala el máximo de puntos")
    void givenAPrecisionThenItScalesTheMaximumPoints(double precision, double expected) {
        PrecisionRule rule = new PrecisionRule("Precisión", PRECISION, 80.0);

        assertThat(subtotal(rule, measuring(PRECISION, precision))).isEqualTo(expected);
    }

    @Test
    @DisplayName("Una precisión fuera de 0 a 1 se rechaza")
    void givenAPrecisionOutOfRangeThenTheEvaluationIsRejected() {
        PrecisionRule rule = new PrecisionRule("Precisión", PRECISION, 80.0);
        RawMetrics outOfRange = measuring(PRECISION, 1.2);

        assertThatThrownBy(() -> rule.evaluate(outOfRange)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} de 4 rescatadas → {1} pts")
    @CsvSource({"4, 100.0", "3, 75.0", "0, 0.0"})
    @DisplayName("Cada víctima rescatada suma")
    void givenRescuedVictimsThenEachRescuedAdds(int rescued, double expected) {
        VictimsRule rule = new VictimsRule("Víctimas rescatadas", RESCUED, 25.0);

        assertThat(subtotal(rule, measuring(RESCUED, rescued))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} de 4 rescatadas → {1} pts")
    @CsvSource({"4, 0.0", "3, -10.0", "0, -40.0"})
    @DisplayName("Cada víctima que no se rescató resta, en una regla aparte")
    void givenRescuedVictimsThenEachAbandonedDeducts(int rescued, double expected) {
        AbandonedVictimsRule rule = new AbandonedVictimsRule("Víctimas abandonadas", RESCUED, new VictimTariff(4, 10.0));

        assertThat(subtotal(rule, measuring(RESCUED, rescued))).isEqualTo(expected);
    }

    @Test
    @DisplayName("No se pueden rescatar más víctimas que las que hay en la pista")
    void givenMoreRescuedThanPlacedThenTheEvaluationIsRejected() {
        AbandonedVictimsRule rule = new AbandonedVictimsRule("Víctimas abandonadas", RESCUED, new VictimTariff(4, 10.0));
        RawMetrics fiveRescued = measuring(RESCUED, 5.0);

        assertThatThrownBy(() -> rule.evaluate(fiveRescued)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} metros con umbral 10 → {1} pts")
    @CsvSource({"12.0, 30.0", "10.0, 30.0", "9.5, 0.0"})
    @DisplayName("El hito alcanzado otorga el bonus completo; sin alcanzarlo, nada")
    void givenAMeasurementThenTheBonusIsGrantedOnlyFromTheThreshold(double meters, double expected) {
        MilestoneBonusRule rule = new MilestoneBonusRule("Bonus por hito", new Milestone(DISTANCE, 10.0), 30.0);

        assertThat(subtotal(rule, measuring(DISTANCE, meters))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} víctimas")
    @CsvSource({"2.7", "-1.0", "NaN"})
    @DisplayName("Un conteo que no es un número entero no negativo se rechaza en vez de truncarse")
    void givenACountThatIsNotAWholeNumberThenTheEvaluationIsRejected(double rescued) {
        VictimsRule rule = new VictimsRule("Víctimas rescatadas", RESCUED, 25.0);
        RawMetrics invalid = measuring(RESCUED, rescued);

        assertThatThrownBy(() -> rule.evaluate(invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Una precisión que no es un número se rechaza")
    void givenANotANumberPrecisionThenTheEvaluationIsRejected() {
        PrecisionRule rule = new PrecisionRule("Precisión", PRECISION, 80.0);
        RawMetrics notANumber = measuring(PRECISION, Double.NaN);

        assertThatThrownBy(() -> rule.evaluate(notANumber)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Sin la medición capturada la regla no inventa un cero: falla nombrando la métrica")
    void givenAMissingMeasurementThenTheRuleFailsNamingTheMetric() {
        CountedFaultRule lineExits = new CountedFaultRule("Salidas de línea", LINE_EXITS, new FaultTariff(0, 5.0));
        RawMetrics withoutExits = RawMetrics.of(60.0, 0, 0);

        assertThatThrownBy(() -> lineExits.evaluate(withoutExits))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("salidas_de_linea");
    }
}
