package com.roboleague.tournament.eligibility;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EligibilityResultTest {

    @Test
    void givenAnEligibleResultThenItHasNoReasons() {
        assertThat(EligibilityResult.eligible().reasons()).isEmpty();
        assertThatThrownBy(() -> new EligibilityResult(true, List.of("Falta el robot")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("an eligible result has no reasons against it: [Falta el robot]");
    }

    @Test
    void givenAnIneligibleResultThenItSaysWhy() {
        assertThat(EligibilityResult.ineligible("Falta el robot").reasons()).containsExactly("Falta el robot");
        assertThatThrownBy(() -> EligibilityResult.ineligible(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("an ineligible result says why");
        assertThatThrownBy(() -> new EligibilityResult(false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("an ineligible result says why");
    }
}
