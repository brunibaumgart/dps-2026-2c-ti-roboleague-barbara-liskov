package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.ScoreRule.RuleEvaluation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A breakdown explains its total: the total is the sum of its items, the zero floor included.
 */
class ScoreBreakdownTest {

    @Test
    @DisplayName("El total de un desglose es la suma de sus ítems, no un dato aparte que pueda diferir")
    void givenABreakdownThenItsTotalIsTheSumOfItsItems() {
        ScoreBreakdown breakdown = new ScoreBreakdown(
                RuleEvaluation.of(ScoreItem.of("Tiempo", "50s", "formula", Points.of(100))),
                RuleEvaluation.of(ScoreItem.of("Checkpoint", "1", "formula", Points.of(30))),
                RuleEvaluation.of(ScoreItem.of("Penalizaciones", "2 faltas", "formula", Points.of(-30))));

        Points itemsSum = breakdown.items().stream().map(ScoreItem::subtotal).reduce(Points.ZERO, Points::plus);
        assertThat(breakdown.totalScore()).isEqualTo(Points.of(100)).isEqualTo(itemsSum);
    }

    @Test
    @DisplayName("El piso en cero aparece en el desglose y la explicación suma el total")
    void givenItemsBelowZeroThenTheFloorIsAnItemAndTheItemsAddUpToTheTotal() {
        ScoreBreakdown breakdown = new ScoreBreakdown(
                RuleEvaluation.of(ScoreItem.of("Tiempo", "90s", "formula", Points.of(10))),
                RuleEvaluation.empty(),
                RuleEvaluation.of(ScoreItem.of("Penalizaciones", "2 faltas", "formula", Points.of(-30))));

        Points itemsSum = breakdown.items().stream().map(ScoreItem::subtotal).reduce(Points.ZERO, Points::plus);
        assertThat(breakdown.totalScore()).isEqualTo(Points.ZERO);
        assertThat(itemsSum).isEqualTo(breakdown.totalScore());
        assertThat(breakdown.items()).extracting(ScoreItem::concept).endsWith("Piso en cero");
    }
}
