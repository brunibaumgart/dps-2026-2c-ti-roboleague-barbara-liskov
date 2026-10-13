package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.BaseRule;
import com.roboleague.evaluation.rules.CompositeScoreRule;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.ScoreRule.RuleEvaluation;
import com.roboleague.evaluation.rules.TimeBasedRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del informe de la Entrega 1, invertidos para describir el comportamiento correcto.
 */
class RulebookReviewFindingsTest {

    private static final List<String> MUTATOR_PREFIXES = List.of("add", "remove", "set", "clear", "replace");

    @Test
    @DisplayName("Hallazgo 1: la regla compuesta del reglamento no expone operaciones para cambiar sus reglas")
    void givenTheCompositeRuleThenItHasNoPublicMutators() {
        List<String> mutators = Arrays.stream(CompositeScoreRule.class.getMethods())
                .filter(method -> method.getDeclaringClass() == CompositeScoreRule.class)
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .map(Method::getName)
                .filter(name -> MUTATOR_PREFIXES.stream().anyMatch(name::startsWith))
                .toList();

        assertThat(mutators).isEmpty();
    }

    @Test
    @DisplayName("Hallazgo 1: la regla compuesta copia sus reglas; cambiar la lista recibida no la modifica")
    void givenTheCompositeRuleThenItsRulesListIsUnmodifiable() {
        List<BaseRule> received = new java.util.ArrayList<>(List.of(TimeBasedRule.standard(Points.of(100), 60.0)));
        CompositeScoreRule composite = new CompositeScoreRule("Laberinto", received);

        received.add(ObjectivesRule.standard(Points.of(15)));

        assertThat(composite.definition().arguments().rules()).hasSize(1);
    }

    @Test
    @DisplayName("Pregunta abierta: el total de un desglose es la suma de sus ítems, no un dato aparte que pueda diferir")
    void givenABreakdownThenItsTotalIsTheSumOfItsItems() {
        ScoreBreakdown breakdown = new ScoreBreakdown(
                RuleEvaluation.of(ScoreItem.of("Tiempo", "50s", "formula", Points.of(100))),
                RuleEvaluation.of(ScoreItem.of("Checkpoint", "1", "formula", Points.of(30))),
                RuleEvaluation.of(ScoreItem.of("Penalizaciones", "2 faltas", "formula", Points.of(-30))));

        Points itemsSum = breakdown.items().stream().map(ScoreItem::subtotal).reduce(Points.ZERO, Points::plus);
        assertThat(breakdown.totalScore()).isEqualTo(Points.of(100)).isEqualTo(itemsSum);
    }

    @Test
    @DisplayName("Pregunta abierta: el piso en cero aparece en el desglose y la explicación suma el total")
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
