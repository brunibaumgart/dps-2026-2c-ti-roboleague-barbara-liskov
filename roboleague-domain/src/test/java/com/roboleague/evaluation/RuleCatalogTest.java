package com.roboleague.evaluation;

import com.roboleague.evaluation.definition.MetricDeclaration;
import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.RuleArguments;
import com.roboleague.evaluation.definition.RuleDefinition;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.evaluation.definition.StrategyDefinition;
import com.roboleague.evaluation.rules.AbandonedVictimsRule;
import com.roboleague.evaluation.rules.AllObjectivesBonusRule;
import com.roboleague.evaluation.rules.CompositeScoreRule;
import com.roboleague.evaluation.rules.CountedFaultRule;
import com.roboleague.evaluation.rules.FaultTariff;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.Milestone;
import com.roboleague.evaluation.rules.MilestoneBonusRule;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.PrecisionRule;
import com.roboleague.evaluation.rules.ResourceConsumptionRule;
import com.roboleague.evaluation.rules.ScoreRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.rules.VictimTariff;
import com.roboleague.evaluation.rules.VictimsRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.BestNOfM;
import com.roboleague.evaluation.scheme.HigherJudgeScore;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.LowerDeductions;
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
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RuleCatalogTest {

    private static final Metric COLLISIONS = Metric.sensor("colisiones");
    private static final Metric PRECISION = Metric.sensor("precision");
    private static final Metric RESCUED = Metric.judged("rescatadas");
    private static final Metric CHECKPOINT = Metric.sensor("checkpoint");
    private static final MetricSheet DECLARED = new MetricSheet(List.of(
            new MetricDefinition(COLLISIONS, MeasurementUnit.COUNT, ValueRange.atLeast(0.0)),
            new MetricDefinition(PRECISION, MeasurementUnit.RATIO, ValueRange.between(0.0, 1.0)),
            new MetricDefinition(RESCUED, MeasurementUnit.COUNT, ValueRange.between(0.0, 4.0)),
            new MetricDefinition(CHECKPOINT, MeasurementUnit.COUNT, ValueRange.between(0.0, 1.0))));
    private static final StrategyDefinition UNLIMITED = new StrategyDefinition(Unlimited.TYPE, Parameters.none());
    private static final RulebookDefinition.Bonuses NO_BONUSES = new RulebookDefinition.Bonuses(List.of(), UNLIMITED);
    private static final StrategyDefinition ALL_ROUNDS = new StrategyDefinition(AllRounds.TYPE, Parameters.none());

    private final RuleCatalog catalog = RuleCatalog.standard();
    private final RawMetrics everything = new RawMetrics(new TrackPerformance(55.0, 4, 2), EvaluationFeedback.of(
            90.0, Map.of("j1", 8.0), Map.of(COLLISIONS.name(), 3.0, PRECISION.name(), 0.8,
                    RESCUED.name(), 3.0, CHECKPOINT.name(), 1.0)));

    private static RulebookDefinition withRule(RuleDefinition rule) {
        return scoring(new RulebookDefinition.Scoring(List.of(rule), NO_BONUSES, List.of()));
    }

    private static RulebookDefinition withBonus(RuleDefinition rule) {
        return scoring(new RulebookDefinition.Scoring(List.of(), new RulebookDefinition.Bonuses(List.of(rule), UNLIMITED),
                List.of()));
    }

    private static RulebookDefinition withDeduction(RuleDefinition rule) {
        return scoring(new RulebookDefinition.Scoring(List.of(), NO_BONUSES, List.of(rule)));
    }

    private static RulebookDefinition scoring(RulebookDefinition.Scoring scoring) {
        return new RulebookDefinition(DECLARED.declarations(), scoring,
                new RulebookDefinition.Ranking(ALL_ROUNDS, List.of("higher-total")));
    }

    private static RulebookDefinition declaring(List<MetricDeclaration> metrics) {
        return new RulebookDefinition(metrics, new RulebookDefinition.Scoring(
                List.of(), NO_BONUSES, List.of(new PenaltyRule("Faltas", 10.0).definition())),
                new RulebookDefinition.Ranking(ALL_ROUNDS, List.of("higher-total")));
    }

    private static MetricDeclaration collisionsWithin(Parameters range) {
        return new MetricDeclaration(COLLISIONS, MeasurementUnit.COUNT, range);
    }

    private static RulebookDefinition.Scoring scoringOf(RulebookAssembly assembly) {
        return ((RulebookAssembly.Assembled) assembly).scoring().definition();
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("everyRuleType")
    @DisplayName("Cada tipo de regla, en la lista de su sección, se describe y se reconstruye igual, y puntúa lo mismo")
    void givenARuleThenItsDefinitionRebuildsAnEqualRuleThatScoresTheSame(
            ScoreRule rule, Function<RuleDefinition, RulebookDefinition> inItsSection) {
        RulebookDefinition definition = inItsSection.apply(rule.definition());

        RulebookAssembly assembly = catalog.assemble(definition);

        assertThat(assembly).isInstanceOf(RulebookAssembly.Assembled.class);
        ScoringScheme rebuilt = ((RulebookAssembly.Assembled) assembly).scoring();
        assertThat(rebuilt.definition()).isEqualTo(definition.scoring());
        assertThat(rebuilt.evaluate(everything).items())
                .startsWith(rule.evaluate(everything).items().toArray(ScoreItem[]::new));
    }

    static Stream<Arguments> everyRuleType() {
        TimeBasedRule time = TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.5, 2.0, 0.0);
        ObjectivesRule objectives = new ObjectivesRule("Objetivos", 20.0);
        Named<Function<RuleDefinition, RulebookDefinition>> base = Named.of("base", RuleCatalogTest::withRule);
        Named<Function<RuleDefinition, RulebookDefinition>> bonus = Named.of("bonificación", RuleCatalogTest::withBonus);
        Named<Function<RuleDefinition, RulebookDefinition>> deduction =
                Named.of("deducción", RuleCatalogTest::withDeduction);
        return Stream.of(
                Arguments.of(Named.of("tiempo", time), base),
                Arguments.of(Named.of("objetivos", objectives), base),
                Arguments.of(Named.of("panel de jueces", new JudgeSubjectiveRule("Jueces", 5.0)), base),
                Arguments.of(Named.of("precisión", new PrecisionRule("Precisión", PRECISION, 50.0)), base),
                Arguments.of(Named.of("víctimas", new VictimsRule("Víctimas", RESCUED, 25.0)), base),
                Arguments.of(Named.of("compuesta", new CompositeScoreRule("Desempeño en pista",
                        List.of(time, objectives))), base),
                Arguments.of(Named.of("hito", new MilestoneBonusRule("Checkpoint", new Milestone(CHECKPOINT, 1.0),
                        30.0)), bonus),
                Arguments.of(Named.of("todos los objetivos", new AllObjectivesBonusRule("Todos los objetivos", 5,
                        25.0)), bonus),
                Arguments.of(Named.of("faltas", new PenaltyRule("Faltas", 15.0)), deduction),
                Arguments.of(Named.of("faltas contadas", new CountedFaultRule("Colisiones", COLLISIONS,
                        new FaultTariff(1, 5.0))), deduction),
                Arguments.of(Named.of("consumo", new ResourceConsumptionRule("Consumo", 80.0, 0.5)), deduction),
                Arguments.of(Named.of("víctimas abandonadas", new AbandonedVictimsRule("Víctimas abandonadas",
                        RESCUED, new VictimTariff(4, 10.0))), deduction)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("misplacedRules")
    @DisplayName("Una regla en la lista de otra sección se rechaza diciendo a qué sección pertenece")
    void givenARuleInTheWrongSectionThenItIsRejectedNamingItsSection(RulebookDefinition definition, String problem) {
        assertThat(((RulebookAssembly.Rejected) catalog.assemble(definition)).problems()).containsExactly(problem);
    }

    static Stream<Arguments> misplacedRules() {
        RuleDefinition penalty = new PenaltyRule("Faltas", 15.0).definition();
        RuleDefinition milestone = new MilestoneBonusRule("Checkpoint", new Milestone(CHECKPOINT, 1.0), 30.0).definition();
        RuleDefinition time = TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.5, 2.0, 0.0).definition();
        return Stream.of(
                Arguments.of(Named.of("penalización entre las bonificaciones", withBonus(penalty)),
                        "rule 'Faltas': type 'penalty' is a deduction, not a bonus"),
                Arguments.of(Named.of("hito entre las reglas base", withRule(milestone)),
                        "rule 'Checkpoint': type 'milestone' is a bonus, not a base rule"),
                Arguments.of(Named.of("tiempo entre las deducciones", withDeduction(time)),
                        "rule 'Tiempo': type 'time' is a base rule, not a deduction"),
                Arguments.of(Named.of("penalización dentro de una compuesta", withRule(new RuleDefinition("composite",
                                "Desempeño", RuleArguments.ofRules(List.of(time, penalty))))),
                        "rule 'Desempeño': rule 'Faltas': type 'penalty' is a deduction, not a base rule")
        );
    }

    @Test
    @DisplayName("El tope, la selección de rondas y los criterios también se describen y se reconstruyen")
    void givenSchemesThenTheirDefinitionsRebuildEqualSchemes() {
        ScoringScheme scoring = new ScoringScheme(DECLARED, new ScoreRules(
                List.of(TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.5, 2.0, 0.0)),
                List.of(new MilestoneBonusRule("Checkpoint", new Milestone(CHECKPOINT, 1.0), 30.0)),
                List.of(new PenaltyRule("Faltas", 10.0))), new CappedAt(40.0));
        RankingScheme ranking = new RankingScheme(new BestNOfM(3, 5),
                List.of(new HigherTotal(), new LowerTime(), new LowerDeductions(), new HigherJudgeScore()));

        RulebookAssembly assembly = catalog.assemble(
                new RulebookDefinition(scoring.metrics().declarations(), scoring.definition(), ranking.definition()));

        RulebookAssembly.Assembled assembled = (RulebookAssembly.Assembled) assembly;
        assertThat(assembled.scoring().definition()).isEqualTo(scoring.definition());
        assertThat(assembled.ranking().definition()).isEqualTo(ranking.definition());
        assertThat(assembled.ranking().roundSelection()).isEqualTo(new BestNOfM(3, 5));
    }

    @Test
    @DisplayName("Las métricas que declara un reglamento se describen y se reconstruyen iguales")
    void givenDeclaredMetricsThenTheirDefinitionsRebuildTheSameSheet() {
        MetricSheet sheet = new MetricSheet(List.of(
                new MetricDefinition(COLLISIONS, MeasurementUnit.COUNT, ValueRange.atLeast(0.0)),
                new MetricDefinition(PRECISION, MeasurementUnit.RATIO, ValueRange.between(0.0, 1.0))));

        RulebookAssembly assembly = catalog.assemble(declaring(sheet.declarations()));

        assertThat(((RulebookAssembly.Assembled) assembly).scoring().metrics()).isEqualTo(sheet);
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
                        withDeduction(new RuleDefinition("penalty", "Faltas", RuleArguments.of(Parameters.none())))),
                        "rule 'Faltas': missing parameter 'deductionPerPenalty'"),
                Arguments.of(Named.of("parámetro inválido",
                        withRule(new RuleDefinition("time", "Tiempo",
                                RuleArguments.of(timeNumbers.with("targetTimeSeconds", 0.0))))),
                        "rule 'Tiempo': targetTimeSeconds must be positive"),
                Arguments.of(Named.of("base negativa", withRule(new RuleDefinition("time", "Tiempo",
                                RuleArguments.of(timeNumbers.with("basePoints", -100.0))))),
                        "rule 'Tiempo': basePoints cannot be negative"),
                Arguments.of(Named.of("métrica faltante",
                        withRule(new RuleDefinition("precision", "Precisión",
                                RuleArguments.of(Parameters.none().with("maxPoints", 50.0))))),
                        "rule 'Precisión': missing metric 'accuracy'"),
                Arguments.of(Named.of("N de M inválido", new RulebookDefinition(List.of(),
                                new RulebookDefinition.Scoring(List.of(time), NO_BONUSES, List.of()),
                                new RulebookDefinition.Ranking(new StrategyDefinition(BestNOfM.TYPE,
                                        Parameters.none().with("considered", 4.0).with("outOf", 3.0)), List.of("higher-total")))),
                        "round selection: considered must be between 1 and outOf"),
                Arguments.of(Named.of("criterio desconocido", new RulebookDefinition(List.of(),
                                new RulebookDefinition.Scoring(List.of(time), NO_BONUSES, List.of()),
                                new RulebookDefinition.Ranking(ALL_ROUNDS, List.of("higher-total", "luck")))),
                        "tie-break criterion 'luck': unknown criterion"),
                Arguments.of(Named.of("sin criterios", new RulebookDefinition(List.of(),
                                new RulebookDefinition.Scoring(List.of(time), NO_BONUSES, List.of()),
                                new RulebookDefinition.Ranking(ALL_ROUNDS, List.of()))),
                        "ranking: a ranking scheme needs at least one criterion"),
                Arguments.of(Named.of("criterio repetido", new RulebookDefinition(List.of(),
                                new RulebookDefinition.Scoring(List.of(time), NO_BONUSES, List.of()),
                                new RulebookDefinition.Ranking(ALL_ROUNDS, List.of("higher-total", "higher-total")))),
                        "ranking: a ranking scheme cannot repeat a criterion"),
                Arguments.of(Named.of("sin el total primero", new RulebookDefinition(List.of(),
                                new RulebookDefinition.Scoring(List.of(time), NO_BONUSES, List.of()),
                                new RulebookDefinition.Ranking(ALL_ROUNDS, List.of("lower-time")))),
                        "ranking: a ranking scheme orders by total before breaking ties"),
                Arguments.of(Named.of("rango invertido", declaring(List.of(
                                collisionsWithin(Parameters.none().with("min", 5.0).with("max", 1.0))))),
                        "metric 'colisiones': max cannot be less than min"),
                Arguments.of(Named.of("rango sin mínimo", declaring(List.of(collisionsWithin(Parameters.none())))),
                        "metric 'colisiones': missing parameter 'min'"),
                Arguments.of(Named.of("métrica declarada dos veces", declaring(List.of(
                                collisionsWithin(Parameters.none().with("min", 0.0)),
                                collisionsWithin(Parameters.none().with("min", 1.0))))),
                        "metrics: metric 'colisiones' is declared twice"),
                Arguments.of(Named.of("parámetro mal escrito", withDeduction(new RuleDefinition("penalty", "Faltas",
                                RuleArguments.of(Parameters.none().with("deductionPerPenalty", 15.0)
                                        .with("deductionPerPenaltyy", 7.0))))),
                        "rule 'Faltas': unknown parameter 'deductionPerPenaltyy'"),
                Arguments.of(Named.of("rol de métrica desconocido", withDeduction(new RuleDefinition("counted-fault",
                                "Colisiones", new CountedFaultRule("Colisiones", COLLISIONS, new FaultTariff(1, 5.0))
                                        .definition().arguments().withMetric("collisions", COLLISIONS)))),
                        "rule 'Colisiones': unknown metric 'collisions'"),
                Arguments.of(Named.of("reglas dentro de una regla que no las lleva", withRule(new RuleDefinition("time",
                                "Tiempo", new RuleArguments(timeNumbers, Map.of(), List.of(time))))),
                        "rule 'Tiempo': rules nested in a rule that takes none"),
                Arguments.of(Named.of("hija de compuesta con un parámetro de más", withRule(new RuleDefinition(
                                "composite", "Desempeño", RuleArguments.ofRules(List.of(new RuleDefinition("time", "Tiempo",
                                        RuleArguments.of(timeNumbers.with("bonus", 5.0)))))))),
                        "rule 'Desempeño': rule 'Tiempo': unknown parameter 'bonus'"),
                Arguments.of(Named.of("tope con un número de más", new RulebookDefinition(DECLARED.declarations(),
                                new RulebookDefinition.Scoring(List.of(time), new RulebookDefinition.Bonuses(List.of(),
                                        new StrategyDefinition(CappedAt.TYPE,
                                                Parameters.none().with("maximum", 40.0).with("max", 30.0))), List.of()),
                                new RulebookDefinition.Ranking(ALL_ROUNDS, List.of("higher-total")))),
                        "bonus limit: unknown parameter 'max'"),
                Arguments.of(Named.of("N de M con un número de más", new RulebookDefinition(DECLARED.declarations(),
                                new RulebookDefinition.Scoring(List.of(time), NO_BONUSES, List.of()),
                                new RulebookDefinition.Ranking(new StrategyDefinition(BestNOfM.TYPE, Parameters.none()
                                        .with("considered", 2.0).with("outOf", 3.0).with("total", 3.0)),
                                        List.of("higher-total")))),
                        "round selection: unknown parameter 'total'"),
                Arguments.of(Named.of("rango con un número desconocido", declaring(List.of(
                                collisionsWithin(Parameters.none().with("min", 0.0).with("maximum", 4.0))))),
                        "metric 'colisiones': unknown parameter 'maximum'"),
                Arguments.of(Named.of("métrica no declarada", withDeduction(new CountedFaultRule("Colisiones",
                                Metric.sensor("colision"), new FaultTariff(1, 5.0)).definition())),
                        "scoring: rule 'Colisiones' reads metric 'colision' (AUTOMATIC_MEASUREMENTS), "
                                + "which the rulebook does not declare"),
                Arguments.of(Named.of("métrica de otra fuente", withDeduction(new CountedFaultRule("Colisiones",
                                Metric.judged("colisiones"), new FaultTariff(1, 5.0)).definition())),
                        "scoring: rule 'Colisiones' reads metric 'colisiones' (JUDGE_PANEL), "
                                + "which the rulebook does not declare")
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
        RulebookDefinition twoBadRules = new RulebookDefinition(List.of(), new RulebookDefinition.Scoring(List.of(
                new RuleDefinition("teleport", "A", RuleArguments.of(Parameters.none())),
                new RuleDefinition("penalty", "B", RuleArguments.of(Parameters.none()))), NO_BONUSES, List.of()),
                new RulebookDefinition.Ranking(ALL_ROUNDS, List.of("higher-total")));

        assertThat(((RulebookAssembly.Rejected) catalog.assemble(twoBadRules)).problems()).hasSize(2);
    }

    @Test
    @DisplayName("Una regla compuesta se reconstruye con sus hijas")
    void givenACompositeThenItsChildrenAreRebuilt() {
        CompositeScoreRule track = new CompositeScoreRule("Desempeño en pista", List.of(
                TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.5, 2.0, 0.0), new ObjectivesRule("Objetivos", 20.0)));

        RulebookDefinition.Scoring rebuilt = scoringOf(catalog.assemble(withRule(track.definition())));

        assertThat(rebuilt.rules().getFirst().arguments().rules()).hasSize(2);
    }
}
