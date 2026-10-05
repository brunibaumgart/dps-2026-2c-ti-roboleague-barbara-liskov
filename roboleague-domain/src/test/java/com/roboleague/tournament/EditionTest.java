package com.roboleague.tournament;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EditionTest {

    private final Edition edition = new Edition(
            new EditionContext(Tournament.of("t-1", "RoboLeague", new Season("s-2026", 2026, "Temporada 2026")),
                    new EditionHeader("ed-2026", "RoboLeague 2026", 1)),
            new DateRange(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12)));

    @Test
    @DisplayName("Una edición no ofrece dos categorías con el mismo id")
    void givenAnOfferedCategoryIdThenAnotherCategoryWithItIsRejected() {
        edition.addCategory(Category.of("cat-junior", "Junior", 2, 4, 12, 17, 2500.0));

        assertThatThrownBy(() -> edition.addCategory(Category.of("cat-junior", "Junior libre", 1, 9, 5, 90, 9000.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Category already offered in this edition: cat-junior");
        assertThat(edition.getCategories()).extracting(Category::name).containsExactly("Junior");
    }
}
