package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.scheduling.RoundId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.roboleague.ranking.StandingsFixtures.*;
import static com.roboleague.support.TestValues.audit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandingsTableTest {

    @Test
    @DisplayName("F1: with best 2 of 3 the total adds the two best rounds and the row shows the discarded one")
    void bestTwoOfThreeCountsTheBestRoundsAndShowsTheDiscardedOne() {
        TeamAttempts alpha = new TeamAttempts(team("t-a"), List.of(
                scored("t-a", "r-1", 50, 3, 0), scored("t-a", "r-2", 50, 5, 0), scored("t-a", "r-3", 50, 4, 0)));

        StandingsEntry row = StandingsTable.rank(BEST_2_OF_3, List.of(alpha)).entries().getFirst();

        assertThat(row.total()).isEqualTo(180.0);
        assertThat(row.rounds().considered()).extracting(RoundResult::roundId)
                .containsExactly(RoundId.of("r-2"), RoundId.of("r-3"));
        assertThat(row.rounds().discarded()).singleElement().satisfies(discarded -> {
            assertThat(discarded.roundId()).isEqualTo(RoundId.of("r-1"));
            assertThat(discarded.attemptId()).isEqualTo(AttemptId.of(slotOf("t-a", "r-1"), 1));
            assertThat(discarded.total()).isEqualTo(60.0);
        });
        assertThat(row.rounds().selectionRule()).isEqualTo("mejores 2 de 3 rondas");
    }

    @ParameterizedTest(name = "second: {0}s, {1} objectives, {2} faults -> {3} / {4}")
    @CsvSource(delimiter = '|', textBlock = """
            50 | 4 | 0 | 2 | Debajo del puesto 1 por Mayor puntaje total
            60 | 5 | 0 | 2 | Empata en puntaje con el puesto 1; desempata por Menor tiempo
            50 | 6 | 2 | 2 | Empata en puntaje con el puesto 1; desempata por Menor descuento por penalizaciones
            50 | 5 | 0 | 1 | Empata con el puesto 1 en todos los criterios
            """)
    @DisplayName("Each row says which criterion of the chain placed it below the previous one, or that they share it")
    void eachRowExplainsItsPlace(double seconds, int objectives, int faults, int position, String explanation) {
        TeamAttempts leader = new TeamAttempts(team("t-a"), List.of(scored("t-a", "r-1", 50, 5, 0)));
        TeamAttempts other = new TeamAttempts(team("t-b"), List.of(scored("t-b", "r-1", seconds, objectives, faults)));

        List<StandingsEntry> rows = StandingsTable.rank(BEST_2_OF_3, List.of(other, leader)).entries();

        assertThat(rows.getFirst().team()).isEqualTo(team("t-a"));
        assertThat(rows.getFirst().placement()).isEqualTo(new Placement(1, "Primer puesto"));
        assertThat(rows.getLast().placement()).isEqualTo(new Placement(position, explanation));
    }

    @Test
    @DisplayName("Teams tied on every criterion share the position and the next one skips it")
    void aFullTieSharesThePositionAndTheNextTeamSkipsIt() {
        List<StandingsEntry> rows = StandingsTable.rank(BEST_2_OF_3, List.of(
                new TeamAttempts(team("t-c"), List.of(scored("t-c", "r-1", 50, 2, 0))),
                new TeamAttempts(team("t-b"), List.of(scored("t-b", "r-1", 50, 5, 0))),
                new TeamAttempts(team("t-a"), List.of(scored("t-a", "r-1", 50, 5, 0))))).entries();

        assertThat(rows).extracting(StandingsEntry::position).containsExactly(1, 1, 3);
        assertThat(rows).extracting(row -> row.team().teamId())
                .containsExactly(TeamId.of("t-a"), TeamId.of("t-b"), TeamId.of("t-c"));
    }

    @Test
    @DisplayName("Hallazgo 3: a team whose only attempt is disqualified gets no round and goes last")
    void aTeamWithOnlyADisqualifiedAttemptGetsNoRoundAndGoesLast() {
        Attempt disqualified = scored("t-a", "r-1", 40, 5, 0);
        disqualified.disqualify("Robot fuera de reglamento", JUDGE, audit());

        List<StandingsEntry> rows = StandingsTable.rank(BEST_2_OF_3, List.of(
                new TeamAttempts(team("t-a"), List.of(disqualified)),
                new TeamAttempts(team("t-b"), List.of(scored("t-b", "r-1", 50, 1, 0))))).entries();

        assertThat(rows.getLast().team()).isEqualTo(team("t-a"));
        assertThat(rows.getLast().total()).isZero();
        assertThat(rows.getLast().rounds().considered()).isEmpty();
    }

    @Test
    @DisplayName("Hallazgo 3: a disqualified attempt does not count even if it scored more than the others")
    void aDisqualifiedAttemptDoesNotCountEvenWithAHigherScore() {
        Attempt disqualified = scored("t-a", "r-2", 40, 5, 0);
        disqualified.disqualify("Robot fuera de reglamento", JUDGE, audit());

        StandingsEntry row = StandingsTable.rank(BEST_2_OF_3, List.of(new TeamAttempts(team("t-a"),
                List.of(scored("t-a", "r-1", 50, 2, 0), disqualified)))).entries().getFirst();

        assertThat(row.total()).isEqualTo(40.0);
        assertThat(row.rounds().considered()).extracting(RoundResult::roundId).containsExactly(RoundId.of("r-1"));
    }

    @Test
    @DisplayName("An attempt still awaiting a source gives the team no round instead of a zero")
    void anAttemptAwaitingASourceGivesNoRound() {
        Attempt awaiting = opened("t-a", "r-1", 1);

        StandingsEntry row = StandingsTable.rank(BEST_2_OF_3,
                List.of(new TeamAttempts(team("t-a"), List.of(awaiting)))).entries().getFirst();

        assertThat(row.rounds().considered()).isEmpty();
        assertThat(row.rounds().discarded()).isEmpty();
    }

    @Test
    @DisplayName("A round with two attempts counts with the best one")
    void aRoundWithTwoAttemptsCountsWithTheBestOne() {
        Attempt second = opened("t-a", "r-1", 2);
        second.receive(new SourceDelivery(run(50, 5, 0), JUDGE), RULEBOOK, audit());

        StandingsEntry row = StandingsTable.rank(BEST_2_OF_3, List.of(new TeamAttempts(team("t-a"),
                List.of(scored("t-a", "r-1", 50, 1, 0), second)))).entries().getFirst();

        assertThat(row.rounds().considered()).singleElement()
                .satisfies(round -> assertThat(round.attemptId()).isEqualTo(second.getId()));
        assertThat(row.total()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("An attempt of another team cannot be listed under a team")
    void attemptsBelongToTheirTeam() {
        assertThatThrownBy(() -> new TeamAttempts(team("t-a"), List.of(scored("t-b", "r-1", 50, 1, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belongs to t-b");
    }
}
