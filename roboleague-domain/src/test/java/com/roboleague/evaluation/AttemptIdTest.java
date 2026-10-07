package com.roboleague.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptIdTest {

    @Test
    void givenASlotAndANumberThenTheIdIsTheSlotDashTheNumber() {
        assertThat(AttemptId.of("slot-7", 2).value()).isEqualTo("slot-7-2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"slot-7-2", "3f2a9c1e-5b7d-4e2a-9f1c-0d8e7b6a5c4f-1", "s-12"})
    void givenAWrittenIdThenParsingItGivesTheSameId(String value) {
        assertThat(AttemptId.parse(value).value()).isEqualTo(value);
    }

    @Test
    void givenAnIdWithADashedSlotThenTheNumberIsWhatFollowsTheLastDash() {
        AttemptId id = AttemptId.parse("3f2a9c1e-5b7d-1");

        assertThat(id.slotId()).isEqualTo("3f2a9c1e-5b7d");
        assertThat(id.number()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"slot", "-1", "slot-", "slot-x", "slot-1a", ""})
    void givenATextWithoutSlotAndNumberThenItIsRejected(String value) {
        assertThatThrownBy(() -> AttemptId.parse(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void givenANumberBelowOneThenItIsRejected(int number) {
        assertThatThrownBy(() -> AttemptId.of("slot-7", number))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void givenABlankSlotThenItIsRejected() {
        assertThatThrownBy(() -> AttemptId.of(" ", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slotId");
    }
}
