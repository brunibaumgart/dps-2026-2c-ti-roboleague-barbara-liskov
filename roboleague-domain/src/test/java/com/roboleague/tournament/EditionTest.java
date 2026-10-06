package com.roboleague.tournament;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest(name = "id \"{0}\", nombre \"{1}\"")
    @CsvSource({"'  ', RoboLeague 2026, edition id cannot be blank", "ed-2026, '  ', edition name cannot be blank"})
    @DisplayName("Una edición no acepta id ni nombre en blanco")
    void givenABlankEditionIdOrNameThenItIsRejected(String id, String name, String problem) {
        assertThatThrownBy(() -> new EditionHeader(id, name, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(problem);
    }

    @ParameterizedTest(name = "id \"{0}\", nombre \"{1}\"")
    @CsvSource({"'  ', Junior, category id cannot be blank", "cat-junior, '  ', category name cannot be blank"})
    @DisplayName("Una categoría no acepta id ni nombre en blanco")
    void givenABlankCategoryIdOrNameThenItIsRejected(String id, String name, String problem) {
        assertThatThrownBy(() -> Category.of(id, name, 2, 4, 12, 17, 2500.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(problem);
    }
}
