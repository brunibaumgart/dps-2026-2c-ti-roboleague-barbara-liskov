package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.*;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
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
    @DisplayName("ObjectiveBonusRule computes points per milestone plus all-completed bonus")
    void objectiveBonusRuleCalculations() {
        // 20 pts per objective, 4 total objectives, 30 pts all-completed bonus
        ObjectiveBonusRule rule = new ObjectiveBonusRule("Hitos de Navegación", new ObjectiveRuleConfig(20.0, 4, 30.0));

        // Scenario 1: 3 out of 4 completed
        RawMetrics metricsPartial = RawMetrics.of(50.0, 3, 0);
        ScoreRule.RuleEvaluation evalPartial = rule.evaluate(metricsPartial);
        // 3 * 20 = 60.0
        assertThat(evalPartial.items().get(0).subtotal()).isEqualTo(60.0);

        // Scenario 2: all 4 completed
        RawMetrics metricsAll = RawMetrics.of(50.0, 4, 0);
        ScoreRule.RuleEvaluation evalAll = rule.evaluate(metricsAll);
        // 4 * 20 + 30 = 110.0
        assertThat(evalAll.items().get(0).subtotal()).isEqualTo(110.0);
        assertThat(evalAll.items().get(0).appliedFormula()).contains("bonificación total");
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
        ScoreRule objRule = new ObjectiveBonusRule("Objetivos", new ObjectiveRuleConfig(25.0, 4, 20.0));
        ScoreRule penaltyRule = new PenaltyRule("Penalizaciones", 10.0);
        ScoreRule judgeRule = new JudgeSubjectiveRule("Jueces", 2.0);

        Rulebook rulebook = new Rulebook(RulebookVersion.first(), ScoringScheme.withoutBonuses(List.of(timeRule, objRule, penaltyRule, judgeRule)),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal())));

        // Time: 50s (+10 bonus => 110)
        // Objectives: 4 (+100 + 20 => 120)
        // Penalties: 2 (-20 => -20)
        // Judges: avg 8.0 * 2.0 = 16.0
        // Expected total = 110 + 120 - 20 + 16 = 226.0
        RawMetrics metrics = new RawMetrics(
                new TrackPerformance(50.0, 4, 2),
                new EvaluationFeedback(0.0, Map.of("j1", 8.0, "j2", 8.0), Map.of())
        );

        ScoreBreakdown breakdown = rulebook.evaluate(metrics);

        assertThat(breakdown.items()).hasSize(4);
        assertThat(breakdown.totalScore()).isEqualTo(226.0);
        assertThat(breakdown.notesAndPenalties()).hasSize(4);

        // Verify each line item exists and is explainable
        assertThat(breakdown.items()).extracting(ScoreItem::concept)
                .containsExactly("Tiempo", "Objetivos", "Penalizaciones", "Jueces");
    }
}
