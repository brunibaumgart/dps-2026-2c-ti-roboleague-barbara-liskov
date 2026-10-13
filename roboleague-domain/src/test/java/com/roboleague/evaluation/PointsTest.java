package com.roboleague.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"10, 10.00", "10.0, 10.00", "10.00, 10.00", "-7.5, -7.50", "0.1, 0.10", "147.5, 147.50"})
    @DisplayName("Points keep two decimals whatever scale they were written with")
    void pointsKeepTwoDecimals(String written, String kept) {
        Points points = Points.of(written);

        assertThat(points.toBigDecimal()).isEqualTo(new BigDecimal(kept));
        assertThat(points).hasToString(kept);
    }

    @Test
    @DisplayName("The same amount is equal however it was built, so it can be a key or a set element")
    void theSameAmountIsEqualHoweverItWasBuilt() {
        List<Points> tens = List.of(Points.of(10), Points.of(10.0), Points.of("10.00"), Points.of(new BigDecimal("1E+1")));

        assertThat(Set.copyOf(tens)).containsExactly(Points.of(10));
        assertThat(tens).allSatisfy(points -> {
            assertThat(points).isEqualTo(Points.of("10")).hasSameHashCodeAs(Points.of("10"));
            assertThat(points.compareTo(Points.of("10"))).isZero();
        });
    }

    @ParameterizedTest(name = "{0} + {1} = {2}, {0} - {1} = {3}")
    @CsvSource({"10.1, 20.2, 30.30, -10.10", "0.1, 0.2, 0.30, -0.10", "100, -30, 70.00, 130.00"})
    @DisplayName("Adding and subtracting are exact")
    void addingAndSubtractingAreExact(String a, String b, String sum, String difference) {
        assertThat(Points.of(a).plus(Points.of(b))).isEqualTo(Points.of(sum));
        assertThat(Points.of(a).minus(Points.of(b))).isEqualTo(Points.of(difference));
    }

    @Test
    @DisplayName("The order of a sum does not change it, unlike adding doubles")
    void theOrderOfASumDoesNotChangeIt() {
        Points ascending = Points.of(10.1).plus(Points.of(20.2)).plus(Points.of(30.3));
        Points descending = Points.of(30.3).plus(Points.of(20.2)).plus(Points.of(10.1));

        assertThat(ascending).isEqualTo(descending).isEqualTo(Points.of("60.60"));
    }

    @ParameterizedTest(name = "{0} * {1} = {2}")
    @CsvSource({
            "100, 0.875, 87.50",
            "1, 0.125, 0.12",
            "1, 0.135, 0.14",
            "1, 0.145, 0.14",
            "1, -0.125, -0.12",
            "10.01, 0.5, 5.00",
            "10.03, 0.5, 5.02"})
    @DisplayName("Scaling by a factor rounds once to hundredths, half to the even neighbour")
    void scalingRoundsHalfToEven(String points, String factor, String expected) {
        assertThat(Points.of(points).times(new BigDecimal(factor))).isEqualTo(Points.of(expected));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"0.125, 0.12", "0.135, 0.14", "2.675, 2.68", "-0.125, -0.12"})
    @DisplayName("A double is read by its shortest decimal, not by its binary approximation")
    void aDoubleIsReadByItsShortestDecimal(double written, String expected) {
        assertThat(Points.of(written)).isEqualTo(Points.of(expected));
    }

    @Test
    @DisplayName("Multiplying by a count, negating, max and min")
    void otherOperations() {
        Points fifteen = Points.of(15);

        assertThat(fifteen.times(3)).isEqualTo(Points.of(45));
        assertThat(fifteen.negate()).isEqualTo(Points.of(-15)).satisfies(negative -> {
            assertThat(negative.isNegative()).isTrue();
            assertThat(negative.abs()).isEqualTo(fifteen);
        });
        assertThat(fifteen.max(Points.ZERO)).isEqualTo(fifteen);
        assertThat(fifteen.negate().max(Points.ZERO)).isEqualTo(Points.ZERO);
        assertThat(fifteen.min(Points.ZERO)).isEqualTo(Points.ZERO);
        assertThat(Points.of("0.00").isZero()).isTrue();
        assertThat(fifteen.isGreaterThan(Points.of("14.99"))).isTrue();
        assertThat(Points.of("12.34").doubleValue()).isEqualTo(12.34);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Points reject NaN and infinite doubles")
    void pointsRejectNonFiniteDoubles(double amount) {
        assertThatThrownBy(() -> Points.of(amount)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "abc", "NaN", "Infinity", "1,5"})
    @DisplayName("Points reject text that is not a decimal number")
    void pointsRejectTextThatIsNotADecimal(String amount) {
        assertThatThrownBy(() -> Points.of(amount)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} fits exactly: {1}")
    @CsvSource({"12.5, true", "12.50, true", "12.500, true", "12.345, false", "1E+3, true"})
    @DisplayName("An amount fits exactly when it has no more than two significant decimals")
    void anAmountFitsExactlyWithTwoDecimals(String amount, boolean fits) {
        assertThat(Points.fitsExactly(new BigDecimal(amount))).isEqualTo(fits);
    }
}
