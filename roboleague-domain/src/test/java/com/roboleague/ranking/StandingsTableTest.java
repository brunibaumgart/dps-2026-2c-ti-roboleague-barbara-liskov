package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.MeasurementUnit;
import com.roboleague.evaluation.Measurements;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.MetricDefinition;
import com.roboleague.evaluation.MetricSheet;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookReference;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoreRules;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.Unlimited;
import com.roboleague.evaluation.ValueRange;
import com.roboleague.evaluation.rules.PrecisionRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.LastAttempt;
import com.roboleague.evaluation.scheme.LowerTime;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.scheduling.RoundId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

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

        StandingsEntry row = StandingsTable.rank(RULEBOOK, List.of(alpha)).entries().getFirst();

        assertThat(row.total()).isEqualTo(Points.of(180));
        assertThat(row.rounds().considered()).extracting(RoundResult::roundId)
                .containsExactly(RoundId.of("r-2"), RoundId.of("r-3"));
        assertThat(row.rounds().discarded()).singleElement().satisfies(discarded -> {
            assertThat(discarded.roundId()).isEqualTo(RoundId.of("r-1"));
            assertThat(discarded.attemptId()).isEqualTo(AttemptId.of(slotOf("t-a", "r-1"), 1));
            assertThat(discarded.total()).isEqualTo(Points.of(60));
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

        List<StandingsEntry> rows = StandingsTable.rank(RULEBOOK, List.of(other, leader)).entries();

        assertThat(rows.getFirst().team()).isEqualTo(team("t-a"));
        assertThat(rows.getFirst().placement()).isEqualTo(new Placement(1, "Primer puesto"));
        assertThat(rows.getLast().placement()).isEqualTo(new Placement(position, explanation));
    }

    @Test
    @DisplayName("Teams tied on every criterion share the position and the next one skips it")
    void aFullTieSharesThePositionAndTheNextTeamSkipsIt() {
        List<StandingsEntry> rows = StandingsTable.rank(RULEBOOK, List.of(
                new TeamAttempts(team("t-c"), List.of(scored("t-c", "r-1", 50, 2, 0))),
                new TeamAttempts(team("t-b"), List.of(scored("t-b", "r-1", 50, 5, 0))),
                new TeamAttempts(team("t-a"), List.of(scored("t-a", "r-1", 50, 5, 0))))).entries();

        assertThat(rows).extracting(StandingsEntry::position).containsExactly(1, 1, 3);
        assertThat(rows).extracting(row -> row.team().teamId())
                .containsExactly(TeamId.of("t-a"), TeamId.of("t-b"), TeamId.of("t-c"));
    }

    @Test
    @DisplayName("A team whose only attempt is disqualified gets no round and goes last")
    void aTeamWithOnlyADisqualifiedAttemptGetsNoRoundAndGoesLast() {
        Attempt disqualified = scored("t-a", "r-1", 40, 5, 0);
        disqualified.disqualify(AuditNote.of(JUDGE.asActorId(), "Robot fuera de reglamento"), audit());

        List<StandingsEntry> rows = StandingsTable.rank(RULEBOOK, List.of(
                new TeamAttempts(team("t-a"), List.of(disqualified)),
                new TeamAttempts(team("t-b"), List.of(scored("t-b", "r-1", 50, 1, 0))))).entries();

        assertThat(rows.getLast().team()).isEqualTo(team("t-a"));
        assertThat(rows.getLast().total()).isEqualTo(Points.ZERO);
        assertThat(rows.getLast().rounds().considered()).isEmpty();
    }

    @Test
    @DisplayName("A disqualified attempt does not count even if it scored more than the others")
    void aDisqualifiedAttemptDoesNotCountEvenWithAHigherScore() {
        Attempt disqualified = scored("t-a", "r-2", 40, 5, 0);
        disqualified.disqualify(AuditNote.of(JUDGE.asActorId(), "Robot fuera de reglamento"), audit());

        StandingsEntry row = StandingsTable.rank(RULEBOOK, List.of(new TeamAttempts(team("t-a"),
                List.of(scored("t-a", "r-1", 50, 2, 0), disqualified)))).entries().getFirst();

        assertThat(row.total()).isEqualTo(Points.of(40));
        assertThat(row.rounds().considered()).extracting(RoundResult::roundId).containsExactly(RoundId.of("r-1"));
    }

    @Test
    @DisplayName("An attempt still awaiting a source gives the team no round instead of a zero")
    void anAttemptAwaitingASourceGivesNoRound() {
        Attempt awaiting = opened("t-a", "r-1", 1);

        StandingsEntry row = StandingsTable.rank(RULEBOOK,
                List.of(new TeamAttempts(team("t-a"), List.of(awaiting)))).entries().getFirst();

        assertThat(row.rounds().considered()).isEmpty();
        assertThat(row.rounds().discarded()).isEmpty();
    }

    @Test
    @DisplayName("A round with two attempts counts with the best one")
    void aRoundWithTwoAttemptsCountsWithTheBestOne() {
        Attempt second = opened("t-a", "r-1", 2);
        second.receive(new SourceDelivery(run(50, 5, 0), JUDGE), RULEBOOK, audit());

        StandingsEntry row = StandingsTable.rank(RULEBOOK, List.of(new TeamAttempts(team("t-a"),
                List.of(scored("t-a", "r-1", 50, 1, 0), second)))).entries().getFirst();

        assertThat(row.rounds().considered()).singleElement()
                .satisfies(round -> assertThat(round.attemptId()).isEqualTo(second.getId()));
        assertThat(row.total()).isEqualTo(Points.of(100));
    }

    @Test
    @DisplayName("F1: the row names the attempt that counted, the other attempts of its turn and the attempt rule")
    void theRowNamesTheOtherAttemptsOfTheTurn() {
        Attempt second = opened("t-a", "r-1", 2);
        second.receive(new SourceDelivery(run(50, 5, 0), JUDGE), RULEBOOK, audit());
        Attempt first = scored("t-a", "r-1", 50, 1, 0);

        StandingsTable table = StandingsTable.rank(RULEBOOK, List.of(new TeamAttempts(team("t-a"), List.of(second, first))));
        StandingsEntry row = table.entries().getFirst();

        assertThat(table.rulebookVersion()).isEqualTo(RULEBOOK.version());
        assertThat(row.rounds().attemptRule()).isEqualTo("1 intento por turno");
        assertThat(row.rounds().considered()).containsExactly(
                new RoundResult(RoundId.of("r-1"), second.getId(), Points.of(100), List.of(first.getId())));
    }

    @Test
    @DisplayName("F1: with the last attempt counting, a later lower score replaces an earlier higher one")
    void withTheLastAttemptCountingTheLaterScoreCounts() {
        Rulebook lastCounts = new Rulebook(RulebookVersion.first(), RULEBOOK.scoring(), new RankingScheme(
                new AllRounds(), new LastAttempt(2), List.of(new HigherTotal())));
        Attempt best = opened("t-a", "r-1", 1);
        best.receive(new SourceDelivery(run(50, 5, 0), JUDGE), lastCounts, audit());
        Attempt last = opened("t-a", "r-1", 2);
        last.receive(new SourceDelivery(run(50, 1, 0), JUDGE), lastCounts, audit());

        StandingsEntry row = StandingsTable.rank(lastCounts, List.of(new TeamAttempts(team("t-a"),
                List.of(best, last)))).entries().getFirst();

        assertThat(row.total()).isEqualTo(Points.of(20));
        assertThat(row.rounds().attemptRule()).isEqualTo("el último de 2 intentos por turno");
        assertThat(row.rounds().considered().getFirst().otherAttempts()).containsExactly(best.getId());
    }

    @Test
    @DisplayName("An attempt of another team cannot be listed under a team")
    void attemptsBelongToTheirTeam() {
        assertThatThrownBy(() -> new TeamAttempts(team("t-a"), List.of(scored("t-b", "r-1", 50, 1, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belongs to t-b");
    }

    @Test
    @DisplayName("Teams with the same round scores in another order tie on the total and the next criterion decides")
    void theSameRoundScoresInAnotherOrderTieOnTheTotal() {
        assertThat(10.1 + 20.2 + 30.3).as("as doubles the order changes the sum").isNotEqualTo(30.3 + 20.2 + 10.1);
        Metric precision = Metric.sensor("precision");
        Rulebook rulebook = new Rulebook(RulebookVersion.first(), new ScoringScheme(
                new MetricSheet(List.of(new MetricDefinition(precision, MeasurementUnit.RATIO, ValueRange.between(0.0, 1.0)))),
                new ScoreRules(List.of(new PrecisionRule("Precisión", precision, Points.of(100))), List.of(), List.of()),
                new Unlimited()), new RankingScheme(new AllRounds(), List.of(new HigherTotal(), new LowerTime())));
        TeamAttempts ascending = new TeamAttempts(team("t-a"), List.of(
                scoredWith(rulebook, "t-a", "r-1", 50, Map.of(precision.name(), 0.101)),
                scoredWith(rulebook, "t-a", "r-2", 50, Map.of(precision.name(), 0.202)),
                scoredWith(rulebook, "t-a", "r-3", 50, Map.of(precision.name(), 0.303))));
        TeamAttempts descending = new TeamAttempts(team("t-b"), List.of(
                scoredWith(rulebook, "t-b", "r-1", 60, Map.of(precision.name(), 0.303)),
                scoredWith(rulebook, "t-b", "r-2", 60, Map.of(precision.name(), 0.202)),
                scoredWith(rulebook, "t-b", "r-3", 60, Map.of(precision.name(), 0.101))));

        List<StandingsEntry> rows = StandingsTable.rank(rulebook, List.of(descending, ascending)).entries();

        assertThat(rows).extracting(StandingsEntry::total).containsOnly(Points.of("60.60"));
        assertThat(rows).extracting(row -> row.team().teamId()).containsExactly(TeamId.of("t-a"), TeamId.of("t-b"));
        assertThat(rows.getLast().placement())
                .isEqualTo(new Placement(2, "Empata en puntaje con el puesto 1; desempata por Menor tiempo"));
    }

    private static Attempt scoredWith(Rulebook rulebook, String teamId, String roundId, double seconds,
                                      Map<String, Double> measured) {
        Attempt attempt = Attempt.of(AttemptIdentity.of(AttemptId.of(slotOf(teamId, roundId), 1), RoundId.of(roundId),
                TeamId.of(teamId)), RulebookReference.of(CHALLENGE, rulebook));
        attempt.receive(new SourceDelivery(new Measurements(new TrackPerformance(seconds, 0, 0), 0.0, measured), JUDGE),
                rulebook, audit());
        return attempt;
    }
}
