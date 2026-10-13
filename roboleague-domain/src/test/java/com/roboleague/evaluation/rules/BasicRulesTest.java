package com.roboleague.evaluation.rules;

import com.roboleague.support.TestValues;
import com.roboleague.evaluation.*;
import com.roboleague.scheduling.JudgeId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BasicRulesTest {

    @Test
    @DisplayName("TimeBasedRule awards bonus points for completing under target time")
    void timeBasedRuleBonusForSpeed() {
        // Base 100, target 60s, +2 pts per second under, -3 pts per second over
        TimeBasedRule rule = new TimeBasedRule("Tiempo de Carrera", TimeRuleConfig.of(Points.of(100), 60.0, 2.0, 3.0, Points.ZERO));

        // Completed in 45 seconds (15 seconds faster)
        RawMetrics metrics = TestValues.metrics(45.0, 0, 0);
        ScoreRule.RuleEvaluation eval = rule.evaluate(metrics);

        assertThat(eval.items()).hasSize(1);
        ScoreItem item = eval.items().get(0);

        // 100 + (15 * 2) = 130.0
        assertThat(item.subtotal()).isEqualTo(Points.of(130));
        assertThat(item.rawMetric()).isEqualTo("45.00 s");
        assertThat(item.appliedFormula()).contains("Base 100.0 + (15.00s por debajo del objetivo * 2.00)");
    }

    @Test
    @DisplayName("La fórmula de objetivos se escribe igual sin importar el idioma de la máquina")
    void objectiveFormulaDoesNotDependOnTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            ScoreItem objectives = new ObjectivesRule("Objetivos", Points.of(20)).evaluate(TestValues.metrics(50.0, 5, 0))
                    .items().getFirst();
            ScoreItem allObjectives = new AllObjectivesBonusRule("Todos los objetivos", 5, Points.of(25))
                    .evaluate(TestValues.metrics(50.0, 5, 0)).items().getFirst();

            assertThat(objectives.appliedFormula()).isEqualTo("5 obj * 20.0 pts");
            assertThat(allObjectives.appliedFormula()).isEqualTo("todos los objetivos: +25.0 pts");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    @DisplayName("TimeBasedRule penalizes when exceeding target time")
    void timeBasedRuleDeductsForSlowness() {
        TimeBasedRule rule = new TimeBasedRule("Tiempo de Carrera", TimeRuleConfig.of(Points.of(100), 60.0, 2.0, 3.0, Points.ZERO));

        // Completed in 70 seconds (10 seconds slower)
        RawMetrics metrics = TestValues.metrics(70.0, 0, 0);
        ScoreRule.RuleEvaluation eval = rule.evaluate(metrics);

        ScoreItem item = eval.items().get(0);
        // 100 - (10 * 3) = 70.0
        assertThat(item.subtotal()).isEqualTo(Points.of(70));
        assertThat(item.appliedFormula()).contains("Base 100.0 - (10.00s por encima del objetivo * 3.00)");
    }

    @Test
    @DisplayName("Los objetivos suman por cada uno; completar todos es una bonificación aparte")
    void objectivesAndTheAllObjectivesBonusAreSeparateRules() {
        ScoreRule objectives = new ObjectivesRule("Hitos de Navegación", Points.of(20));
        ScoreRule allObjectives = new AllObjectivesBonusRule("Todos los hitos", 4, Points.of(30));
        RawMetrics threeOfFour = TestValues.metrics(50.0, 3, 0);
        RawMetrics fourOfFour = TestValues.metrics(50.0, 4, 0);

        assertThat(objectives.evaluate(threeOfFour).total()).isEqualTo(Points.of(60));
        assertThat(allObjectives.evaluate(threeOfFour).total()).isEqualTo(Points.ZERO);
        assertThat(objectives.evaluate(fourOfFour).total()).isEqualTo(Points.of(80));
        assertThat(allObjectives.evaluate(fourOfFour).total()).isEqualTo(Points.of(30));
    }

    @Test
    @DisplayName("PenaltyRule deducts exact points per infraction")
    void penaltyRuleCalculations() {
        PenaltyRule rule = new PenaltyRule("Penalizaciones en Pista", Points.of(15));

        RawMetrics metrics = TestValues.metrics(50.0, 2, 3); // 3 penalties
        ScoreRule.RuleEvaluation eval = rule.evaluate(metrics);

        ScoreItem item = eval.items().get(0);
        assertThat(item.subtotal()).isEqualTo(Points.of(-45));
        assertThat(eval.notes()).anyMatch(n -> n.contains("3 faltas (-45.0 pts)"));
    }

    @Test
    @DisplayName("JudgeSubjectiveRule averages scores from all judges and applies weight")
    void judgeSubjectiveRuleCalculations() {
        JudgeSubjectiveRule rule = new JudgeSubjectiveRule("Puntuación de Innovación", 5.0);

        Map<JudgeId, Double> judgeScores = Map.of(
                JudgeId.of("judge-1"), 8.0,
                JudgeId.of("judge-2"), 9.0,
                JudgeId.of("judge-3"), 10.0
        ); // Average = 9.0

        RawMetrics metrics = TestValues.metrics(50.0, 2, 0, judgeScores);
        ScoreRule.RuleEvaluation eval = rule.evaluate(metrics);

        ScoreItem item = eval.items().get(0);
        // 9.0 * 5.0 = 45.0
        assertThat(item.subtotal()).isEqualTo(Points.of(45));
        assertThat(item.rawMetric()).contains("Promedio 9.00 (3 jueces)");
    }

}
