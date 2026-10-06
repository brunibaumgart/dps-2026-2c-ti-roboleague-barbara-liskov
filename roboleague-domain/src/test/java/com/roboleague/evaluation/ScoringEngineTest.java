package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.*;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringEngineTest {

    @Test
    @DisplayName("TimeBasedRule awards bonus points for completing under target time")
    void timeBasedRuleBonusForSpeed() {
        // Base 100, target 60s, +2 pts per second under, -3 pts per second over
        TimeBasedRule rule = new TimeBasedRule("Tiempo de Carrera", TimeRuleConfig.of(100.0, 60.0, 2.0, 3.0, 0.0));

        // Completed in 45 seconds (15 seconds faster)
        RawMetrics metrics = RawMetrics.of(45.0, 0, 0);
        ScoreRule.RuleEvaluation eval = rule.evaluate(metrics);

        assertThat(eval.items()).hasSize(1);
        ScoreItem item = eval.items().get(0);

        // 100 + (15 * 2) = 130.0
        assertThat(item.subtotal()).isEqualTo(130.0);
        assertThat(item.rawMetric()).isEqualTo("45.00 s");
        assertThat(item.appliedFormula()).contains("Base 100.0 + (15.00s por debajo del objetivo * 2.00)");
    }

    @Test
    @DisplayName("La fórmula de objetivos se escribe igual sin importar el idioma de la máquina")
    void objectiveFormulaDoesNotDependOnTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            ScoreItem objectives = new ObjectivesRule("Objetivos", 20.0).evaluate(RawMetrics.of(50.0, 5, 0))
                    .items().getFirst();
            ScoreItem allObjectives = new AllObjectivesBonusRule("Todos los objetivos", 5, 25.0)
                    .evaluate(RawMetrics.of(50.0, 5, 0)).items().getFirst();

            assertThat(objectives.appliedFormula()).isEqualTo("5 obj * 20.0 pts");
            assertThat(allObjectives.appliedFormula()).isEqualTo("todos los objetivos: +25.0 pts");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    @DisplayName("TimeBasedRule penalizes when exceeding target time")
    void timeBasedRuleDeductsForSlowness() {
        TimeBasedRule rule = new TimeBasedRule("Tiempo de Carrera", TimeRuleConfig.of(100.0, 60.0, 2.0, 3.0, 0.0));

        // Completed in 70 seconds (10 seconds slower)
        RawMetrics metrics = RawMetrics.of(70.0, 0, 0);
        ScoreRule.RuleEvaluation eval = rule.evaluate(metrics);

        ScoreItem item = eval.items().get(0);
        // 100 - (10 * 3) = 70.0
        assertThat(item.subtotal()).isEqualTo(70.0);
        assertThat(item.appliedFormula()).contains("Base 100.0 - (10.00s por encima del objetivo * 3.00)");
    }

    @Test
    @DisplayName("Los objetivos suman por cada uno; completar todos es una bonificación aparte")
    void objectivesAndTheAllObjectivesBonusAreSeparateRules() {
        ScoreRule objectives = new ObjectivesRule("Hitos de Navegación", 20.0);
        ScoreRule allObjectives = new AllObjectivesBonusRule("Todos los hitos", 4, 30.0);
        RawMetrics threeOfFour = RawMetrics.of(50.0, 3, 0);
        RawMetrics fourOfFour = RawMetrics.of(50.0, 4, 0);

        assertThat(objectives.evaluate(threeOfFour).total()).isEqualTo(60.0);
        assertThat(allObjectives.evaluate(threeOfFour).total()).isZero();
        assertThat(objectives.evaluate(fourOfFour).total()).isEqualTo(80.0);
        assertThat(allObjectives.evaluate(fourOfFour).total()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("PenaltyRule deducts exact points per infraction")
    void penaltyRuleCalculations() {
        PenaltyRule rule = new PenaltyRule("Penalizaciones en Pista", 15.0);

        RawMetrics metrics = RawMetrics.of(50.0, 2, 3); // 3 penalties
        ScoreRule.RuleEvaluation eval = rule.evaluate(metrics);

        ScoreItem item = eval.items().get(0);
        assertThat(item.subtotal()).isEqualTo(-45.0);
        assertThat(eval.notes()).anyMatch(n -> n.contains("3 faltas (-45.0 pts)"));
    }

    @Test
    @DisplayName("JudgeSubjectiveRule averages scores from all judges and applies weight")
    void judgeSubjectiveRuleCalculations() {
        JudgeSubjectiveRule rule = new JudgeSubjectiveRule("Puntuación de Innovación", 5.0);

        Map<String, Double> judgeScores = Map.of(
                "judge-1", 8.0,
                "judge-2", 9.0,
                "judge-3", 10.0
        ); // Average = 9.0

        RawMetrics metrics = RawMetrics.of(50.0, 2, 0, judgeScores);
        ScoreRule.RuleEvaluation eval = rule.evaluate(metrics);

        ScoreItem item = eval.items().get(0);
        // 9.0 * 5.0 = 45.0
        assertThat(item.subtotal()).isEqualTo(45.0);
        assertThat(item.rawMetric()).contains("Promedio 9.00 (3 jueces)");
    }

    @Test
    @DisplayName("A rulebook consolidates rules from both sources into an explainable ScoreBreakdown")
    void rulebookConsolidatesExplainableBreakdown() {
        ScoreRule timeRule = new TimeBasedRule("Tiempo", TimeRuleConfig.of(100.0, 60.0, 1.0, 2.0, 0.0));
        ScoreRule objRule = new ObjectivesRule("Objetivos", 25.0);
        ScoreRule allObjectives = new AllObjectivesBonusRule("Todos los objetivos", 4, 20.0);
        ScoreRule penaltyRule = new PenaltyRule("Penalizaciones", 10.0);
        ScoreRule judgeRule = new JudgeSubjectiveRule("Jueces", 2.0);

        Rulebook rulebook = new Rulebook(RulebookVersion.first(), new ScoringScheme(MetricSheet.none(),
                new ScoreRules(List.of(timeRule, objRule, penaltyRule, judgeRule), List.of(allObjectives), List.of()),
                new Unlimited()), new RankingScheme(new AllRounds(), List.of(new HigherTotal())));

        // Time: 50s (+10 bonus => 110)
        // Objectives: 4 * 25 = 100, plus the all-objectives bonus +20 => 120
        // Penalties: 2 (-20 => -20)
        // Judges: avg 8.0 * 2.0 = 16.0
        // Expected total = 110 + 120 - 20 + 16 = 226.0
        RawMetrics metrics = new RawMetrics(
                new TrackPerformance(50.0, 4, 2),
                new EvaluationFeedback(0.0, Map.of("j1", 8.0, "j2", 8.0), Map.of())
        );

        ScoreBreakdown breakdown = rulebook.evaluate(metrics);

        assertThat(breakdown.items()).hasSize(5);
        assertThat(breakdown.totalScore()).isEqualTo(226.0);
        assertThat(breakdown.notesAndPenalties()).hasSize(5);

        // Verify each line item exists and is explainable
        assertThat(breakdown.items()).extracting(ScoreItem::concept)
                .containsExactly("Tiempo", "Objetivos", "Penalizaciones", "Jueces", "Todos los objetivos");
    }
}
