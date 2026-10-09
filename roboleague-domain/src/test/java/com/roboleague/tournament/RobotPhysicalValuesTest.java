package com.roboleague.tournament;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RobotPhysicalValuesTest {
    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void physicalValuesMustBeFinite(double invalid) {
        assertThatThrownBy(() -> new Weight(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Dimensions(invalid, 100, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Dimensions(100, invalid, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Dimensions(100, 100, invalid)).isInstanceOf(IllegalArgumentException.class);
    }
}
