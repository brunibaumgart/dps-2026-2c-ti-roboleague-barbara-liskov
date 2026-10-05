package com.roboleague.evaluation.scheme;

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
            new HigherTotal(), new LowerTime(), new FewerPenalties(), new HigherJudgeScore()));

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
                Arguments.of("Menor tiempo", allOf(round("r1", 200.0, 45.0, 0)), allOf(round("r1", 200.0, 50.0, 0))),
                Arguments.of("Menos faltas", allOf(round("r1", 200.0, 45.0, 1)), allOf(round("r1", 200.0, 45.0, 3))),
                Arguments.of("Mayor nota de jueces",
                        allOf(withJudges("r1", 200.0, 45.0, 1, Map.of("j1", 9.0))),
                        allOf(withJudges("r1", 200.0, 45.0, 1, Map.of("j1", 7.0))))
        );
    }

    @Test
    @DisplayName("Iguales en todos los criterios: empate")
    void givenEqualScoresOnEveryCriterionThenTheyAreTied() {
        ChallengeScore a = allOf(round("r1", 200.0, 45.0, 1));
        ChallengeScore b = allOf(round("r1", 200.0, 45.0, 1));

        assertThat(fourCriteria.decide(a, b)).isInstanceOf(TieBreakDecision.Tied.class);
        assertThat(fourCriteria.compare(a, b)).isZero();
    }

    @Test
    @DisplayName("El orden de los criterios es el que declara el reglamento")
    void givenCriteriaInAnotherOrderThenTheOrderChangesTheWinner() {
        ChallengeScore fastWithFouls = allOf(round("r1", 200.0, 40.0, 3));
        ChallengeScore slowClean = allOf(round("r1", 200.0, 50.0, 0));
        RankingScheme timeFirst = new RankingScheme(new AllRounds(), List.of(new LowerTime(), new FewerPenalties()));
        RankingScheme foulsFirst = new RankingScheme(new AllRounds(), List.of(new FewerPenalties(), new LowerTime()));

        assertThat(timeFirst.compare(fastWithFouls, slowClean)).isNegative();
        assertThat(foulsFirst.compare(fastWithFouls, slowClean)).isPositive();
    }

    @Test
    @DisplayName("Un equipo sin rondas queda detrás en tiempo y en nota de jueces")
    void givenATeamWithoutRoundsThenItGoesAfterOneWithRounds() {
        ChallengeScore withRound = allOf(withJudges("r1", 0.0, 50.0, 0, Map.of("j1", 5.0)));
        ChallengeScore withoutRounds = allOf();
        RankingScheme timeThenJudges = new RankingScheme(new AllRounds(),
                List.of(new LowerTime(), new HigherJudgeScore()));

        assertThat(timeThenJudges.compare(withRound, withoutRounds)).isNegative();
        assertThat(new HigherJudgeScore().compare(withRound, withoutRounds)).isNegative();
    }

    @Test
    @DisplayName("Rondas sin panel de jueces no cuentan como nota cero")
    void givenRoundsWithoutJudgesThenTheTeamHasNoJudgeScoreAndGoesAfterOneThatHasIt() {
        ChallengeScore withoutJudges = allOf(round("r1", 200.0));
        ChallengeScore withLowJudgeScore = allOf(withJudges("r1", 200.0, 60.0, 0, Map.of("j1", 0.5)));

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
}
