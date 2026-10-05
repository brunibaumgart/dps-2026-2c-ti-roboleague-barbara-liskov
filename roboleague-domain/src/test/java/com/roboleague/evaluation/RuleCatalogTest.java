package com.roboleague.evaluation;

import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.RuleArguments;
import com.roboleague.evaluation.definition.RuleDefinition;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.evaluation.definition.StrategyDefinition;
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
import com.roboleague.evaluation.scheme.BestNOfM;
import com.roboleague.evaluation.scheme.FewerPenalties;
import com.roboleague.evaluation.scheme.HigherJudgeScore;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.LowerTime;
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

class RuleCatalogTest {

    private static final Metric COLLISIONS = Metric.sensor("colisiones");
    private static final Metric PRECISION = Metric.sensor("precision");
    private static final Metric RESCUED = Metric.judged("rescatadas");
    private static final Metric CHECKPOINT = Metric.sensor("checkpoint");
    private static final StrategyDefinition UNLIMITED = new StrategyDefinition(Unlimited.TYPE, Parameters.none());
    private static final StrategyDefinition ALL_ROUNDS = new StrategyDefinition(AllRounds.TYPE, Parameters.none());

    private final RuleCatalog catalog = RuleCatalog.standard();
    private final RawMetrics everything = new RawMetrics(new TrackPerformance(55.0, 4, 2), EvaluationFeedback.of(
            90.0, Map.of("j1", 8.0), Map.of(COLLISIONS.name(), 3.0, PRECISION.name(), 0.8,
                    RESCUED.name(), 3.0, CHECKPOINT.name(), 1.0)));

    private static RulebookDefinition withRule(RuleDefinition rule) {
        return new RulebookDefinition(new RulebookDefinition.Scoring(List.of(rule), List.of(), UNLIMITED),
                new RulebookDefinition.Ranking(ALL_ROUNDS, List.of("higher-total")));
    }

    private static RulebookDefinition.Scoring scoringOf(RulebookAssembly assembly) {
        return ((RulebookAssembly.Assembled) assembly).scoring().definition();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyRuleType")
    @DisplayName("Cada tipo de regla se describe y se reconstruye igual, y puntúa lo mismo")
    void givenARuleThenItsDefinitionRebuildsAnEqualRuleThatScoresTheSame(ScoreRule rule) {
        RulebookAssembly assembly = catalog.assemble(withRule(rule.definition()));

        assertThat(assembly).isInstanceOf(RulebookAssembly.Assembled.class);
        ScoringScheme rebuilt = ((RulebookAssembly.Assembled) assembly).scoring();
        assertThat(rebuilt.definition().rules()).containsExactly(rule.definition());
        assertThat(rebuilt.evaluate(everything)).isEqualTo(rule.evaluate(everything));
    }

    static Stream<Named<ScoreRule>> everyRuleType() {
        TimeBasedRule time = TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.5, 2.0, 0.0);
        ObjectiveBonusRule objectives = ObjectiveBonusRule.of("Objetivos", 20.0, 5, 25.0);
        return Stream.of(
                Named.of("tiempo", time),
                Named.of("objetivos", objectives),
                Named.of("faltas", new PenaltyRule("Faltas", 15.0)),
                Named.of("panel de jueces", new JudgeSubjectiveRule("Jueces", 5.0)),
                Named.of("consumo", new ResourceConsumptionRule("Consumo", 80.0, 0.5)),
                Named.of("faltas contadas", new CountedFaultRule("Colisiones", COLLISIONS, new FaultTariff(1, 5.0))),
                Named.of("precisión", new PrecisionRule("Precisión", PRECISION, 50.0)),
                Named.of("víctimas", new VictimsRule("Víctimas", RESCUED, new VictimTariff(4, 25.0, 10.0))),
                Named.of("hito", new MilestoneBonusRule("Checkpoint", new Milestone(CHECKPOINT, 1.0), 30.0)),
                Named.of("compuesta", new CompositeScoreRule("Desempeño en pista", List.of(time, objectives)))
        );
    }

    @Test
    @DisplayName("El tope, la selección de rondas y los criterios también se describen y se reconstruyen")
    void givenSchemesThenTheirDefinitionsRebuildEqualSchemes() {
        ScoringScheme scoring = new ScoringScheme(List.of(new PenaltyRule("Faltas", 10.0)),
                List.of(new MilestoneBonusRule("Checkpoint", new Milestone(CHECKPOINT, 1.0), 30.0)), new CappedAt(40.0));
        RankingScheme ranking = new RankingScheme(new BestNOfM(3, 5),
                List.of(new HigherTotal(), new LowerTime(), new FewerPenalties(), new HigherJudgeScore()));

        RulebookAssembly assembly = catalog.assemble(new RulebookDefinition(scoring.definition(), ranking.definition()));

        RulebookAssembly.Assembled assembled = (RulebookAssembly.Assembled) assembly;
        assertThat(assembled.scoring().definition()).isEqualTo(scoring.definition());
        assertThat(assembled.ranking().definition()).isEqualTo(ranking.definition());
        assertThat(assembled.ranking().roundSelection()).isEqualTo(new BestNOfM(3, 5));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRulebooks")
    @DisplayName("Una definición inválida no lanza: devuelve el problema nombrando qué parte lo tiene")
    void givenAnInvalidDefinitionThenTheAssemblyIsRejectedNamingTheProblem(RulebookDefinition definition,
                                                                           String expectedProblem) {
        RulebookAssembly assembly = catalog.assemble(definition);

        assertThat(assembly).isInstanceOf(RulebookAssembly.Rejected.class);
        assertThat(((RulebookAssembly.Rejected) assembly).problems()).anyMatch(problem -> problem.contains(expectedProblem));
    }

    static Stream<Arguments> invalidRulebooks() {
        RuleDefinition time = TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.5, 2.0, 0.0).definition();
        Parameters timeNumbers = time.arguments().numbers();
        return Stream.of(
                Arguments.of(Named.of("tipo de regla desconocido",
                        withRule(new RuleDefinition("teleport", "Teletransporte", RuleArguments.of(Parameters.none())))),
                        "rule 'Teletransporte': unknown rule type 'teleport'"),
                Arguments.of(Named.of("parámetro faltante",
                        withRule(new RuleDefinition("penalty", "Faltas", RuleArguments.of(Parameters.none())))),
                        "rule 'Faltas': missing parameter 'deductionPerPenalty'"),
                Arguments.of(Named.of("parámetro inválido",
                        withRule(new RuleDefinition("time", "Tiempo",
                                RuleArguments.of(timeNumbers.with("targetTimeSeconds", 0.0))))),
                        "rule 'Tiempo': targetTimeSeconds must be positive"),
                Arguments.of(Named.of("métrica faltante",
                        withRule(new RuleDefinition("precision", "Precisión",
                                RuleArguments.of(Parameters.none().with("maxPoints", 50.0))))),
                        "rule 'Precisión': missing metric 'accuracy'"),
                Arguments.of(Named.of("N de M inválido", new RulebookDefinition(
                                new RulebookDefinition.Scoring(List.of(time), List.of(), UNLIMITED),
                                new RulebookDefinition.Ranking(new StrategyDefinition(BestNOfM.TYPE,
                                        Parameters.none().with("considered", 4.0).with("outOf", 3.0)), List.of("higher-total")))),
                        "round selection: considered must be between 1 and outOf"),
                Arguments.of(Named.of("criterio desconocido", new RulebookDefinition(
                                new RulebookDefinition.Scoring(List.of(time), List.of(), UNLIMITED),
                                new RulebookDefinition.Ranking(ALL_ROUNDS, List.of("higher-total", "luck")))),
                        "tie-break criterion 'luck': unknown criterion"),
                Arguments.of(Named.of("sin criterios", new RulebookDefinition(
                                new RulebookDefinition.Scoring(List.of(time), List.of(), UNLIMITED),
                                new RulebookDefinition.Ranking(ALL_ROUNDS, List.of()))),
                        "ranking: a ranking scheme needs at least one criterion"),
                Arguments.of(Named.of("criterio repetido", new RulebookDefinition(
                                new RulebookDefinition.Scoring(List.of(time), List.of(), UNLIMITED),
                                new RulebookDefinition.Ranking(ALL_ROUNDS, List.of("higher-total", "higher-total")))),
                        "ranking: a ranking scheme cannot repeat a criterion")
        );
    }

    @Test
    @DisplayName("Un problema dentro de una regla compuesta nombra a la regla hija")
    void givenAnInvalidChildThenTheProblemNamesTheChild() {
        RuleDefinition brokenTime = new RuleDefinition("time", "Tiempo", RuleArguments.of(Parameters.none()));
        RuleDefinition track = new RuleDefinition("composite", "Desempeño en pista",
                RuleArguments.ofRules(List.of(brokenTime)));

        assertThat(((RulebookAssembly.Rejected) catalog.assemble(withRule(track))).problems())
                .singleElement().asString()
                .contains("rule 'Desempeño en pista': rule 'Tiempo': missing parameter 'basePoints'");
    }

    @Test
    @DisplayName("Todos los problemas de una definición se informan juntos")
    void givenSeveralProblemsThenAllAreReported() {
        RulebookDefinition twoBadRules = new RulebookDefinition(new RulebookDefinition.Scoring(List.of(
                new RuleDefinition("teleport", "A", RuleArguments.of(Parameters.none())),
                new RuleDefinition("penalty", "B", RuleArguments.of(Parameters.none()))), List.of(), UNLIMITED),
                new RulebookDefinition.Ranking(ALL_ROUNDS, List.of("higher-total")));

        assertThat(((RulebookAssembly.Rejected) catalog.assemble(twoBadRules)).problems()).hasSize(2);
    }

    @Test
    @DisplayName("Una regla compuesta se reconstruye con sus hijas")
    void givenACompositeThenItsChildrenAreRebuilt() {
        CompositeScoreRule track = new CompositeScoreRule("Desempeño en pista", List.of(
                TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.5, 2.0, 0.0), ObjectiveBonusRule.of("Objetivos", 20.0, 5, 25.0)));

        RulebookDefinition.Scoring rebuilt = scoringOf(catalog.assemble(withRule(track.definition())));

        assertThat(rebuilt.rules().getFirst().arguments().rules()).hasSize(2);
    }
}
