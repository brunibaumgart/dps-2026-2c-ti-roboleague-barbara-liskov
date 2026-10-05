package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.CompositeScoreRule;
import com.roboleague.evaluation.rules.FaultTariff;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.Milestone;
import com.roboleague.evaluation.rules.MilestoneBonusRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.PrecisionRule;
import com.roboleague.evaluation.rules.ResourceConsumptionRule;
import com.roboleague.evaluation.rules.TimeAdjustments;
import com.roboleague.evaluation.rules.TimeTargets;
import com.roboleague.evaluation.rules.VictimTariff;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("Hallazgo 1: la lista de reglas que expone la regla compuesta no se puede modificar")
    void givenTheCompositeRuleThenItsRulesListIsUnmodifiable() {
        CompositeScoreRule composite = new CompositeScoreRule("Laberinto", List.of(new PenaltyRule("Faltas", 10.0)));

        assertThatThrownBy(() -> composite.getRules().add(new PenaltyRule("Faltas extra", 15.0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Pregunta abierta: un desglose no acepta un total distinto de la suma de sus ítems")
    void givenItemsThatAddUpToTenThenATotalOfFiveHundredIsRejected() {
        List<ScoreItem> items = List.of(ScoreItem.of("Tiempo", "50s", "formula", 10.0));

        assertThatThrownBy(() -> new ScoreBreakdown(items, List.of(), 500.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Pregunta abierta: el piso en cero aparece en el desglose y la explicación suma el total")
    void givenItemsBelowZeroThenTheFloorIsAnItemAndTheItemsAddUpToTheTotal() {
        ScoreBreakdown breakdown = ScoreBreakdown.of(List.of(
                ScoreItem.of("Tiempo", "90s", "formula", 10.0),
                ScoreItem.of("Penalizaciones", "2 faltas", "formula", -30.0)
        ), List.of());

        double itemsSum = breakdown.items().stream().mapToDouble(ScoreItem::subtotal).sum();
        assertThat(breakdown.totalScore()).isZero();
        assertThat(itemsSum).isEqualTo(breakdown.totalScore());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rulesWithNegativeParameters")
    @DisplayName("Pregunta abierta: una regla rechaza parámetros negativos")
    void givenANegativeParameterThenTheRuleIsRejected(ThrowingCallable creation) {
        assertThatThrownBy(creation).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Named<ThrowingCallable>> rulesWithNegativeParameters() {
        return Stream.of(
                Named.of("penalización con deducción negativa", () -> new PenaltyRule("Penalizaciones", -10.0)),
                Named.of("consumo con penalización negativa", () -> new ResourceConsumptionRule("Consumo", 100.0, -2.0)),
                Named.of("consumo con máximo negativo", () -> new ResourceConsumptionRule("Consumo", -1.0, 2.0)),
                Named.of("jueces con peso negativo", () -> new JudgeSubjectiveRule("Jueces", -1.0)),
                Named.of("tiempo con base negativa", () -> TimeTargets.of(-100.0, 60.0)),
                Named.of("tiempo con bonificación negativa", () -> TimeAdjustments.of(-1.5, 2.0, 0.0)),
                Named.of("tiempo con deducción negativa", () -> TimeAdjustments.of(1.5, -2.0, 0.0)),
                Named.of("tiempo con mínimo negativo", () -> TimeAdjustments.of(1.5, 2.0, -5.0)),
                Named.of("faltas contadas con deducción negativa", () -> new FaultTariff(1, -5.0)),
                Named.of("faltas contadas con franquicia negativa", () -> new FaultTariff(-1, 5.0)),
                Named.of("precisión con máximo negativo", () -> new PrecisionRule("Precisión", Metric.sensor("precision"), -80.0)),
                Named.of("víctimas con deducción negativa", () -> new VictimTariff(4, 25.0, -10.0)),
                Named.of("hito con bonus negativo", () -> new MilestoneBonusRule("Hito",
                        new Milestone(Metric.sensor("distancia_metros"), 10.0), -30.0)),
                Named.of("tope de bonificaciones negativo", () -> new CappedAt(-10.0)),
                Named.of("tope de bonificaciones que no es un número", () -> new CappedAt(Double.NaN))
        );
    }
}
