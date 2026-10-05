package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.CompositeScoreRule;
import com.roboleague.evaluation.rules.CountedFaultRule;
import com.roboleague.evaluation.rules.FaultTariff;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.Milestone;
import com.roboleague.evaluation.rules.MilestoneBonusRule;
import com.roboleague.evaluation.rules.ObjectiveBonusRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.PrecisionRule;
import com.roboleague.evaluation.rules.ResourceConsumptionRule;
import com.roboleague.evaluation.rules.ScoreRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.rules.VictimTariff;
import com.roboleague.evaluation.rules.VictimsRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RulebookSourcesTest {

    private static final RankingScheme ANY_SCHEME = new RankingScheme(new AllRounds(), List.of(new HigherTotal()));
    private static final ScoreRule TIME = TimeBasedRule.standard(100.0, 60.0);
    private static final ScoreRule OBJECTIVES = ObjectiveBonusRule.standard(20.0, 5);
    private static final ScoreRule JUDGES = JudgeSubjectiveRule.standard(2.0);
    private static final Metric RESCUED = Metric.judged("victimas_rescatadas");
    private static final ScoreRule VICTIMS = new VictimsRule("Víctimas", RESCUED, new VictimTariff(4, 25.0, 10.0));

    private static Rulebook rulebookWith(List<ScoreRule> rules) {
        return new Rulebook(RulebookVersion.first(), ScoringScheme.withoutBonuses(rules), ANY_SCHEME);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("rulesAndTheirSource")
    @DisplayName("Cada regla declara la fuente de lo que lee")
    void givenARuleThenItDeclaresTheSourceOfWhatItReads(ScoreRule rule, ResultSource expected) {
        assertThat(rule.source()).isEqualTo(expected);
    }

    static Stream<Arguments> rulesAndTheirSource() {
        ResultSource sensors = ResultSource.AUTOMATIC_MEASUREMENTS;
        ResultSource judges = ResultSource.JUDGE_PANEL;
        return Stream.of(
                Arguments.of(Named.of("Tiempo", TIME), sensors),
                Arguments.of(Named.of("Objetivos", OBJECTIVES), sensors),
                Arguments.of(Named.of("Faltas", new PenaltyRule("Faltas", 10.0)), sensors),
                Arguments.of(Named.of("Consumo", new ResourceConsumptionRule("Consumo", 100.0, 1.0)), sensors),
                Arguments.of(Named.of("Panel de jueces", JUDGES), judges),
                Arguments.of(Named.of("Salidas de línea", new CountedFaultRule("Salidas de línea",
                        Metric.sensor("salidas_de_linea"), new FaultTariff(1, 5.0))), sensors),
                Arguments.of(Named.of("Precisión", new PrecisionRule("Precisión", Metric.sensor("precision"), 80.0)), sensors),
                Arguments.of(Named.of("Víctimas", VICTIMS), judges),
                Arguments.of(Named.of("Bonus por hito", new MilestoneBonusRule("Bonus por hito",
                        new Milestone(Metric.sensor("distancia_metros"), 10.0), 30.0)), sensors)
        );
    }

    @Test
    @DisplayName("\"Desempeño en pista\" suma Tiempo y Objetivos y conserva cada ítem")
    void givenTheTrackPerformanceCompositeThenItAddsTimeAndObjectivesKeepingEachItem() {
        CompositeScoreRule trackPerformance = new CompositeScoreRule("Desempeño en pista", List.of(TIME, OBJECTIVES));

        ScoreRule.RuleEvaluation evaluation = trackPerformance.evaluate(RawMetrics.of(50.0, 5, 0));

        assertThat(evaluation.items()).hasSize(2);
        assertThat(evaluation.items().stream().mapToDouble(ScoreItem::subtotal).sum()).isEqualTo(115.0 + 125.0);
        assertThat(trackPerformance.source()).isEqualTo(ResultSource.AUTOMATIC_MEASUREMENTS);
    }

    @Test
    @DisplayName("Una regla compuesta sin reglas no se puede crear")
    void givenNoRulesThenTheCompositeIsRejected() {
        assertThatThrownBy(() -> new CompositeScoreRule("Vacía", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one rule");
    }

    @Test
    @DisplayName("Una regla compuesta no puede mezclar fuentes")
    void givenRulesFromDifferentSourcesThenTheCompositeIsRejected() {
        assertThatThrownBy(() -> new CompositeScoreRule("Mezcla", List.of(TIME, JUDGES)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un reglamento solo de sensores exige una fuente")
    void givenOnlySensorRulesThenTheRulebookRequiresOneSource() {
        assertThat(rulebookWith(List.of(TIME, OBJECTIVES)).requiredSources())
                .containsExactly(ResultSource.AUTOMATIC_MEASUREMENTS);
    }

    @Test
    @DisplayName("Un reglamento mixto exige sensores y panel de jueces")
    void givenAMixedRulebookThenItRequiresBothSources() {
        assertThat(rulebookWith(List.of(TIME, VICTIMS, JUDGES)).requiredSources())
                .containsExactlyInAnyOrder(ResultSource.AUTOMATIC_MEASUREMENTS, ResultSource.JUDGE_PANEL);
    }

    @Test
    @DisplayName("Sin tope, las contribuciones separan los ítems por fuente y entre las dos suman el total")
    void givenAMixedRulebookThenContributionsSplitItemsBySourceAndAddUpToTheTotal() {
        Rulebook rescue = rulebookWith(List.of(TIME, VICTIMS, JUDGES));
        RawMetrics metrics = new RawMetrics(new TrackPerformance(50.0, 0, 0),
                EvaluationFeedback.of(0.0, Map.of("j1", 8.0), Map.of(RESCUED.name(), 3.0)));

        List<SourceContribution> contributions = rescue.contributions(metrics);

        assertThat(contributions).extracting(SourceContribution::source)
                .containsExactly(ResultSource.AUTOMATIC_MEASUREMENTS, ResultSource.JUDGE_PANEL);
        assertThat(contributions.get(0).items()).extracting(ScoreItem::concept).containsExactly("Regla de Tiempo");
        assertThat(contributions.get(1).items()).extracting(ScoreItem::concept)
                .containsExactly("Víctimas: rescatadas", "Víctimas: abandonadas", "Evaluación de Jueces");
        assertThat(contributions.get(0).subtotal() + contributions.get(1).subtotal())
                .isEqualTo(rescue.evaluate(metrics).totalScore());
    }

    @Test
    @DisplayName("El piso en cero queda en el desglose total, no en una fuente")
    void givenANegativeSumThenTheFloorIsInTheBreakdownButNotInAnySource() {
        Rulebook rescue = rulebookWith(List.of(VICTIMS));
        RawMetrics noneRescued = new RawMetrics(new TrackPerformance(50.0, 0, 0),
                EvaluationFeedback.withMeasurements(Map.of(RESCUED.name(), 0.0)));

        List<SourceContribution> contributions = rescue.contributions(noneRescued);

        assertThat(contributions.getFirst().subtotal()).isEqualTo(-40.0);
        assertThat(rescue.evaluate(noneRescued).totalScore()).isZero();
    }
}
