package com.roboleague.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ValueRangeTest {

    @ParameterizedTest(name = "[0, 4] cubre [{0}, {1}] → {2}")
    @CsvSource({"0.0, 4.0, true", "1.0, 3.0, true", "0.0, 5.0, false", "-1.0, 4.0, false"})
    @DisplayName("Un rango cubre a otro cuando todo valor del segundo cabe en el primero")
    void givenABoundedRangeThenItCoversOnlyRangesInsideIt(double min, double max, boolean covered) {
        assertThat(ValueRange.between(0.0, 4.0).covers(ValueRange.between(min, max))).isEqualTo(covered);
    }

    @ParameterizedTest(name = "al menos 0 cubre {0} → {1}")
    @CsvSource({"between 0 and 100, true", "at least 0, true", "at least -1, false"})
    @DisplayName("Un rango abierto hacia arriba cubre todo lo que empiece en su mínimo o después")
    void givenAnOpenRangeThenItCoversWhatStartsAtItsMinimumOrAfter(String other, boolean covered) {
        ValueRange range = other.equals("between 0 and 100") ? ValueRange.between(0.0, 100.0)
                : other.equals("at least 0") ? ValueRange.atLeast(0.0) : ValueRange.atLeast(-1.0);

        assertThat(ValueRange.atLeast(0.0).covers(range)).isEqualTo(covered);
    }

    @ParameterizedTest(name = "[0, 1] cubre al menos {0} → false")
    @CsvSource({"0.0", "0.5"})
    @DisplayName("Un rango acotado nunca cubre a uno abierto hacia arriba")
    void givenABoundedRangeThenItNeverCoversAnOpenOne(double min) {
        assertThat(ValueRange.between(0.0, 1.0).covers(ValueRange.atLeast(min))).isFalse();
    }
}
