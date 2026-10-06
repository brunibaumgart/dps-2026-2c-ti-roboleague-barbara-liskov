package com.roboleague.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricDefinitionTest {

    private static final MetricDefinition COLLISIONS =
            new MetricDefinition(Metric.sensor("colisiones"), MeasurementUnit.COUNT, ValueRange.atLeast(0.0));
    private static final MetricDefinition PRECISION =
            new MetricDefinition(Metric.sensor("precision"), MeasurementUnit.RATIO, ValueRange.between(0.0, 1.0));

    @ParameterizedTest(name = "colisiones = {0}")
    @ValueSource(doubles = {0.0, 3.0, 120.0})
    @DisplayName("Un conteo entero y dentro del rango se acepta")
    void givenAWholeCountInRangeThenItIsAccepted(double value) {
        assertThat(COLLISIONS.problemWith(value)).isEmpty();
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("valuesOutsideTheirDefinition")
    @DisplayName("Un valor que no es de la unidad o queda fuera del rango se rechaza nombrando la métrica")
    void givenAValueOutsideTheDefinitionThenTheProblemNamesTheMetric(MetricDefinition definition, double value,
                                                                     String problem) {
        assertThat(definition.problemWith(value)).contains(problem);
    }

    static Stream<Arguments> valuesOutsideTheirDefinition() {
        return Stream.of(
                Arguments.of(Named.of("colisiones", COLLISIONS), 2.5,
                        "measurement 'colisiones' is not a valid COUNT: 2.5"),
                Arguments.of(Named.of("colisiones", COLLISIONS), -1.0,
                        "measurement 'colisiones' must be at least 0.0: -1.0"),
                Arguments.of(Named.of("precision", PRECISION), 1.4,
                        "measurement 'precision' must be between 0.0 and 1.0: 1.4"),
                Arguments.of(Named.of("precision", PRECISION), Double.NaN,
                        "measurement 'precision' must be a finite number: NaN"));
    }

    @Test
    @DisplayName("Una proporción acepta decimales dentro del rango")
    void givenARatioThenDecimalsInRangeAreAccepted() {
        assertThat(PRECISION.problemWith(0.85)).isEmpty();
    }

    @Test
    @DisplayName("Un rango con el máximo menor que el mínimo no se puede declarar")
    void givenAMaximumBelowTheMinimumThenTheRangeIsRejected() {
        assertThatThrownBy(() -> ValueRange.between(5.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("max cannot be less than min: 1.0 < 5.0");
    }
}
