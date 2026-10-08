package com.roboleague.evaluation.scheme;

import com.roboleague.evaluation.EvaluationFeedback;
import com.roboleague.evaluation.MeasurementUnit;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.MetricDefinition;
import com.roboleague.evaluation.MetricSheet;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoreRules;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.Unlimited;
import com.roboleague.evaluation.ValueRange;
import com.roboleague.evaluation.audit.EvaluationSnapshot;
import com.roboleague.evaluation.rules.CountedFaultRule;
import com.roboleague.evaluation.rules.FaultTariff;
import com.roboleague.evaluation.rules.PrecisionRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.RoundId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.roboleague.evaluation.scheme.RoundScores.allOf;
import static com.roboleague.evaluation.scheme.RoundScores.round;
import static com.roboleague.evaluation.scheme.RoundScores.withJudges;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingSchemeTest {

    private final RankingScheme fourCriteria = new RankingScheme(new AllRounds(), List.of(
            new HigherTotal(), new LowerTime(), new LowerDeductions(), new HigherJudgeScore()));

    @ParameterizedTest(name = "{0}")
    @MethodSource("pairsDecidedByEachCriterion")
    @DisplayName("El primer criterio que separa a dos equipos decide y dice su nombre")
    void givenTwoTeamsThenTheFirstCriterionThatSeparatesThemIsNamed(String criterion, ChallengeScore winner,
                                                                    ChallengeScore loser) {
        TieBreakDecision decision = fourCriteria.decide(winner, loser);

        assertThat(decision).isInstanceOf(TieBreakDecision.DecidedBy.class);
        TieBreakDecision.DecidedBy decided = (TieBreakDecision.DecidedBy) decision;
        assertThat(decided.criterion()).isEqualTo(criterion);
        assertThat(decided.order()).isNegative();
        assertThat(fourCriteria.compare(loser, winner)).isPositive();
    }

    static Stream<Arguments> pairsDecidedByEachCriterion() {
        return Stream.of(
                Arguments.of("Mayor puntaje total", allOf(round("r1", 200.0)), allOf(round("r1", 150.0))),
                Arguments.of("Menor tiempo", allOf(round("r1", 200.0, 45.0, 0.0)), allOf(round("r1", 200.0, 50.0, 0.0))),
                Arguments.of("Menor descuento por penalizaciones", allOf(round("r1", 200.0, 45.0, 10.0)),
                        allOf(round("r1", 200.0, 45.0, 30.0))),
                Arguments.of("Mayor nota de jueces",
                        allOf(withJudges("r1", 200.0, 45.0, 10.0, Map.of(JudgeId.of("j1"), 9.0))),
                        allOf(withJudges("r1", 200.0, 45.0, 10.0, Map.of(JudgeId.of("j1"), 7.0))))
        );
    }

    @Test
    @DisplayName("Iguales en todos los criterios: empate")
    void givenEqualScoresOnEveryCriterionThenTheyAreTied() {
        ChallengeScore a = allOf(round("r1", 200.0, 45.0, 10.0));
        ChallengeScore b = allOf(round("r1", 200.0, 45.0, 10.0));

        assertThat(fourCriteria.decide(a, b)).isInstanceOf(TieBreakDecision.Tied.class);
        assertThat(fourCriteria.compare(a, b)).isZero();
    }

    @Test
    @DisplayName("El orden de los desempates es el que declara el reglamento")
    void givenTieBreakersInAnotherOrderThenTheOrderChangesTheWinner() {
        ChallengeScore fastWithFouls = allOf(round("r1", 200.0, 40.0, 30.0));
        ChallengeScore slowClean = allOf(round("r1", 200.0, 50.0, 0.0));
        RankingScheme timeFirst = new RankingScheme(new AllRounds(),
                List.of(new HigherTotal(), new LowerTime(), new LowerDeductions()));
        RankingScheme foulsFirst = new RankingScheme(new AllRounds(),
                List.of(new HigherTotal(), new LowerDeductions(), new LowerTime()));

        assertThat(timeFirst.compare(fastWithFouls, slowClean)).isNegative();
        assertThat(foulsFirst.compare(fastWithFouls, slowClean)).isPositive();
    }

    @Test
    @DisplayName("Un equipo sin rondas queda detrás en tiempo, en descuento y en nota de jueces")
    void givenATeamWithoutRoundsThenItGoesAfterOneWithRounds() {
        ChallengeScore withRound = allOf(withJudges("r1", 0.0, 50.0, 0.0, Map.of(JudgeId.of("j1"), 5.0)));
        ChallengeScore withoutRounds = allOf();
        RankingScheme timeThenJudges = new RankingScheme(new AllRounds(),
                List.of(new HigherTotal(), new LowerTime(), new HigherJudgeScore()));

        assertThat(timeThenJudges.compare(withRound, withoutRounds)).isNegative();
        assertThat(new HigherJudgeScore().compare(withRound, withoutRounds)).isNegative();
        assertThat(new LowerDeductions().compare(withRound, withoutRounds)).isNegative();
    }

    @Test
    @DisplayName("Seguidor de línea: con el mismo total, desempata quien perdió menos puntos por salidas de línea")
    void givenALineFollowerTieThenTheTeamWithFewerDeductedPointsWins() {
        Metric precision = Metric.sensor("precision");
        Metric lineExits = Metric.sensor("salidas_de_linea");
        ScoringScheme lineFollower = new ScoringScheme(new MetricSheet(List.of(
                new MetricDefinition(precision, MeasurementUnit.RATIO, ValueRange.between(0.0, 1.0)),
                new MetricDefinition(lineExits, MeasurementUnit.COUNT, ValueRange.atLeast(0.0)))),
                new ScoreRules(
                        List.of(TimeBasedRule.of("Tiempo de vuelta", 100.0, 90.0, 1.0, 1.0, 0.0),
                                new PrecisionRule("Precisión", precision, 50.0)),
                        List.of(),
                        List.of(new CountedFaultRule("Salidas de línea", lineExits, new FaultTariff(2, 10.0)))),
                new Unlimited());
        ChallengeScore fastWithExits = allOf(scoredRound(lineFollower, 80.0,
                Map.of(precision.name(), 0.8, lineExits.name(), 3.0)));
        ChallengeScore slowClean = allOf(scoredRound(lineFollower, 90.0,
                Map.of(precision.name(), 0.8, lineExits.name(), 2.0)));
        RankingScheme scheme = new RankingScheme(new AllRounds(),
                List.of(new HigherTotal(), new LowerDeductions(), new LowerTime()));

        assertThat(fastWithExits.total()).isEqualTo(140.0).isEqualTo(slowClean.total());
        assertThat(scheme.decide(slowClean, fastWithExits))
                .isEqualTo(new TieBreakDecision.DecidedBy("Menor descuento por penalizaciones", -1));
    }

    private static RoundScore scoredRound(ScoringScheme scoring, double seconds, Map<String, Double> measurements) {
        RawMetrics run = new RawMetrics(new TrackPerformance(seconds, 0, 0),
                EvaluationFeedback.withMeasurements(measurements));
        return new RoundScore(RoundId.of("r1"), new EvaluationSnapshot(RulebookVersion.first(), run, scoring.evaluate(run)));
    }

    @Test
    @DisplayName("Rondas sin panel de jueces no cuentan como nota cero")
    void givenRoundsWithoutJudgesThenTheTeamHasNoJudgeScoreAndGoesAfterOneThatHasIt() {
        ChallengeScore withoutJudges = allOf(round("r1", 200.0));
        ChallengeScore withLowJudgeScore = allOf(withJudges("r1", 200.0, 60.0, 0.0, Map.of(JudgeId.of("j1"), 0.5)));

        assertThat(withoutJudges.judgeScore()).isEmpty();
        assertThat(new HigherJudgeScore().compare(withLowJudgeScore, withoutJudges)).isNegative();
    }

    @Test
    @DisplayName("Dos equipos sin rondas empatan")
    void givenTwoTeamsWithoutRoundsThenTheyAreTied() {
        assertThat(fourCriteria.decide(allOf(), allOf())).isInstanceOf(TieBreakDecision.Tied.class);
    }

    @Test
    @DisplayName("Un esquema sin criterios de desempate no se puede declarar")
    void givenNoCriteriaThenTheSchemeIsRejected() {
        assertThatThrownBy(() -> new RankingScheme(new AllRounds(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un criterio no se puede repetir en la cadena")
    void givenARepeatedCriterionThenTheSchemeIsRejected() {
        assertThatThrownBy(() -> new RankingScheme(new AllRounds(),
                List.of(new HigherTotal(), new LowerTime(), new HigherTotal())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a ranking scheme cannot repeat a criterion");
    }

    @Test
    @DisplayName("El puntaje total ordena antes que cualquier desempate")
    void givenAChainThatDoesNotStartWithTheTotalThenTheSchemeIsRejected() {
        assertThatThrownBy(() -> new RankingScheme(new AllRounds(), List.of(new LowerTime(), new HigherTotal())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a ranking scheme orders by total before breaking ties: 'higher-total' goes first");
    }
}
