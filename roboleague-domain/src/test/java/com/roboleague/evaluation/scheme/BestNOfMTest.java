package com.roboleague.evaluation.scheme;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.roboleague.evaluation.scheme.RoundScores.round;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BestNOfMTest {

    @ParameterizedTest(name = "mejores {0} de {1} con {2} → consideradas {3}, total {4}")
    @MethodSource("selections")
    @DisplayName("Las mejores N de M suman solo las rondas consideradas y descartan el resto")
    void givenRoundTotalsThenOnlyTheBestNCountAndTheRestAreDiscarded(int n, int m, List<Double> totals,
                                                                     List<String> considered, double expectedTotal) {
        List<RoundScore> rounds = new ArrayList<>();
        for (Double total : totals) {
            rounds.add(round("r" + (rounds.size() + 1), total));
        }

        ChallengeScore score = new BestNOfM(n, m).select(rounds);

        assertThat(score.considered()).extracting(RoundScore::roundId).containsExactlyElementsOf(considered);
        assertThat(score.considered().size() + score.discarded().size()).isEqualTo(rounds.size());
        assertThat(score.total()).isEqualTo(expectedTotal);
        assertThat(score.selectionRule()).isEqualTo("mejores " + n + " de " + m + " rondas");
    }

    static Stream<Arguments> selections() {
        return Stream.of(
                Arguments.of(3, 5, List.of(80.0, 95.0, 60.0, 95.0, 70.0), List.of("r1", "r2", "r4"), 270.0),
                Arguments.of(1, 3, List.of(50.0, 70.0, 60.0), List.of("r2"), 70.0),
                Arguments.of(2, 3, List.of(10.0, 10.0, 10.0), List.of("r1", "r2"), 20.0)
        );
    }

    @Test
    @DisplayName("Con menos rondas que N, todas cuentan y no se descarta ninguna")
    void givenFewerRoundsThanNThenAllAreConsideredAndNoneDiscarded() {
        ChallengeScore score = new BestNOfM(3, 5).select(List.of(round("r1", 40.0), round("r2", 50.0)));

        assertThat(score.considered()).hasSize(2);
        assertThat(score.discarded()).isEmpty();
    }

    @Test
    @DisplayName("Un equipo no puede tener más rondas que las M del reglamento")
    void givenMoreRoundsThanMThenTheSelectionIsRejected() {
        BestNOfM bestOfTwo = new BestNOfM(1, 2);
        List<RoundScore> three = List.of(round("r1", 1.0), round("r2", 2.0), round("r3", 3.0));

        assertThatThrownBy(() -> bestOfTwo.select(three)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un equipo no puede tener dos puntajes para la misma ronda")
    void givenTwoScoresForTheSameRoundThenTheSelectionIsRejected() {
        BestNOfM bestOfThree = new BestNOfM(2, 3);
        List<RoundScore> repeated = List.of(round("r1", 10.0), round("r1", 20.0));

        assertThatThrownBy(() -> bestOfThree.select(repeated)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "mejores {0} de {1}")
    @CsvSource({"0,3", "4,3", "2,0"})
    @DisplayName("N y M inválidos se rechazan")
    void givenInvalidNOrMThenTheSelectionIsRejected(int n, int m) {
        assertThatThrownBy(() -> new BestNOfM(n, m)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Todas las rondas: ninguna se descarta")
    void givenAllRoundsThenNothingIsDiscarded() {
        ChallengeScore score = new AllRounds().select(List.of(round("r1", 10.0), round("r2", 20.0)));

        assertThat(score.discarded()).isEmpty();
        assertThat(score.total()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Sin rondas no hay tiempo ni nota de jueces, y el total es cero")
    void givenNoRoundsThenThereIsNoBestTimeNorJudgeScore() {
        ChallengeScore score = new BestNOfM(2, 3).select(List.of());

        assertThat(score.total()).isZero();
        assertThat(score.bestTime()).isEmpty();
        assertThat(score.judgeScore()).isEmpty();
    }
}
