package com.roboleague.evaluation;

import com.roboleague.support.TestValues;
import com.roboleague.evaluation.rules.AbandonedVictimsRule;
import com.roboleague.evaluation.rules.AllObjectivesBonusRule;
import com.roboleague.evaluation.rules.BaseRule;
import com.roboleague.evaluation.rules.BonusRule;
import com.roboleague.evaluation.rules.DeductionRule;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.rules.TimeRuleConfig;
import com.roboleague.evaluation.rules.VictimTariff;
import com.roboleague.evaluation.rules.VictimsRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.scheduling.JudgeId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sources a rulebook requires, what each source contributes and how the rules of every section add up.
 */
class RulebookSourcesTest {

    private static final RankingScheme ANY_SCHEME = new RankingScheme(new AllRounds(), List.of(new HigherTotal()));
    private static final BaseRule TIME = TimeBasedRule.standard(Points.of(100), 60.0);
    private static final BaseRule OBJECTIVES = ObjectivesRule.standard(Points.of(20));
    private static final BaseRule JUDGES = new JudgeSubjectiveRule("Evaluación de Jueces", 2.0);
    private static final Metric RESCUED = Metric.judged("victimas_rescatadas");
    private static final BaseRule VICTIMS = new VictimsRule("Víctimas rescatadas", RESCUED, Points.of(25));
    private static final DeductionRule ABANDONED =
            new AbandonedVictimsRule("Víctimas abandonadas", RESCUED, new VictimTariff(4, Points.of(10)));

    private static Rulebook rulebookWith(List<BaseRule> base, List<DeductionRule> deductions) {
        MetricSheet declared = new MetricSheet(List.of(
                new MetricDefinition(RESCUED, MeasurementUnit.COUNT, ValueRange.between(0.0, 4.0))));
        return new Rulebook(RulebookVersion.first(),
                new ScoringScheme(declared, new ScoreRules(base, List.of(), deductions), new Unlimited()), ANY_SCHEME);
    }

    @Test
    @DisplayName("Un reglamento solo de sensores exige una fuente")
    void givenOnlySensorRulesThenTheRulebookRequiresOneSource() {
        assertThat(rulebookWith(List.of(TIME, OBJECTIVES), List.of()).requiredSources())
                .containsExactly(ResultSource.AUTOMATIC_MEASUREMENTS);
    }

    @Test
    @DisplayName("Un reglamento mixto exige sensores y panel de jueces")
    void givenAMixedRulebookThenItRequiresBothSources() {
        assertThat(rulebookWith(List.of(TIME, VICTIMS, JUDGES), List.of()).requiredSources())
                .containsExactlyInAnyOrder(ResultSource.AUTOMATIC_MEASUREMENTS, ResultSource.JUDGE_PANEL);
    }

    @Test
    @DisplayName("Sin tope, las contribuciones separan los ítems por fuente y entre las dos suman el total")
    void givenAMixedRulebookThenContributionsSplitItemsBySourceAndAddUpToTheTotal() {
        Rulebook rescue = rulebookWith(List.of(TIME, VICTIMS, JUDGES), List.of(ABANDONED));
        RawMetrics metrics = new RawMetrics(new TrackPerformance(50.0, 0, 0),
                EvaluationFeedback.of(0.0, Map.of(JudgeId.of("j1"), 8.0), Map.of(RESCUED.name(), 3.0)));

        List<SourceContribution> contributions = rescue.contributions(metrics);

        assertThat(contributions).extracting(SourceContribution::source)
                .containsExactly(ResultSource.AUTOMATIC_MEASUREMENTS, ResultSource.JUDGE_PANEL);
        assertThat(contributions.get(0).items()).extracting(ScoreItem::concept).containsExactly("Regla de Tiempo");
        assertThat(contributions.get(1).items()).extracting(ScoreItem::concept)
                .containsExactly("Víctimas rescatadas", "Evaluación de Jueces", "Víctimas abandonadas");
        assertThat(contributions.get(0).subtotal().plus(contributions.get(1).subtotal()))
                .isEqualTo(rescue.evaluate(metrics).totalScore());
    }

    @Test
    @DisplayName("El piso en cero queda en el desglose total, no en una fuente")
    void givenANegativeSumThenTheFloorIsInTheBreakdownButNotInAnySource() {
        Rulebook rescue = rulebookWith(List.of(VICTIMS), List.of(ABANDONED));
        RawMetrics noneRescued = new RawMetrics(new TrackPerformance(50.0, 0, 0),
                TestValues.measured(Map.of(RESCUED.name(), 0.0)));

        List<SourceContribution> contributions = rescue.contributions(noneRescued);

        assertThat(contributions.getFirst().subtotal()).isEqualTo(Points.of(-40));
        assertThat(rescue.evaluate(noneRescued).totalScore()).isEqualTo(Points.ZERO);
    }

    @Test
    @DisplayName("A rulebook consolidates rules from both sources into an explainable ScoreBreakdown")
    void rulebookConsolidatesExplainableBreakdown() {
        BaseRule timeRule = new TimeBasedRule("Tiempo", TimeRuleConfig.of(Points.of(100), 60.0, 1.0, 2.0, Points.ZERO));
        BaseRule objRule = new ObjectivesRule("Objetivos", Points.of(25));
        BonusRule allObjectives = new AllObjectivesBonusRule("Todos los objetivos", 4, Points.of(20));
        DeductionRule penaltyRule = new PenaltyRule("Penalizaciones", Points.of(10));
        BaseRule judgeRule = new JudgeSubjectiveRule("Jueces", 2.0);

        Rulebook rulebook = new Rulebook(RulebookVersion.first(), new ScoringScheme(MetricSheet.none(),
                new ScoreRules(List.of(timeRule, objRule, judgeRule), List.of(allObjectives), List.of(penaltyRule)),
                new Unlimited()), new RankingScheme(new AllRounds(), List.of(new HigherTotal())));

        // Time: 50s (+10 bonus => 110)
        // Objectives: 4 * 25 = 100, plus the all-objectives bonus +20 => 120
        // Penalties: 2 (-20 => -20)
        // Judges: avg 8.0 * 2.0 = 16.0
        // Expected total = 110 + 120 - 20 + 16 = 226.0
        RawMetrics metrics = new RawMetrics(
                new TrackPerformance(50.0, 4, 2),
                new EvaluationFeedback(0.0, Map.of(JudgeId.of("j1"), 8.0, JudgeId.of("j2"), 8.0), Map.of())
        );

        ScoreBreakdown breakdown = rulebook.evaluate(metrics);

        assertThat(breakdown.items()).hasSize(5);
        assertThat(breakdown.totalScore()).isEqualTo(Points.of(226));
        assertThat(breakdown.base().total()).isEqualTo(Points.of(110.0 + 100.0 + 16.0));
        assertThat(breakdown.bonuses().total()).isEqualTo(Points.of(20));
        assertThat(breakdown.deductions().total()).isEqualTo(Points.of(-20));
        assertThat(breakdown.notesAndPenalties()).hasSize(5);

        // Verify each line item exists and is explainable
        assertThat(breakdown.items()).extracting(ScoreItem::concept)
                .containsExactly("Tiempo", "Objetivos", "Jueces", "Todos los objetivos", "Penalizaciones");
    }
}
