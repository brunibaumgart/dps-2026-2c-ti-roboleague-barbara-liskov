package com.roboleague.evaluation;

import com.roboleague.evaluation.Attempt.AttemptStatus;
import com.roboleague.evaluation.audit.AttemptEvent;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.rules.VictimsRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * F3: the automatic measurements and the judge panel reach the same attempt separately.
 */
class AttemptSourcesTest {

    private static final Metric RESCUED = Metric.judged("victimas_rescatadas");
    private static final RankingScheme RANKING = new RankingScheme(new AllRounds(), List.of(new HigherTotal()));
    private static final Rulebook RESCUE = new Rulebook(RulebookVersion.first(), new ScoringScheme(
            new MetricSheet(List.of(new MetricDefinition(RESCUED, MeasurementUnit.COUNT, ValueRange.between(0.0, 4.0)))),
            new ScoreRules(List.of(new ObjectivesRule("Zonas despejadas", 15.0),
                    new VictimsRule("Víctimas rescatadas", RESCUED, 25.0),
                    new JudgeSubjectiveRule("Panel técnico", 5.0)), List.of(), List.of()),
            new Unlimited()), RANKING);
    private static final Rulebook MAZE = new Rulebook(RulebookVersion.first(),
            ScoringScheme.withoutBonuses(List.of(TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.0, 2.0, 0.0)), List.of()),
            RANKING);

    // 3 zones => 45; 2 victims => 50; judges 8 and 6 => average 7 * 5 = 35
    private static final SourceDelivery SENSORS = new SourceDelivery(
            new Measurements(new TrackPerformance(90.0, 3, 0), 0.0, Map.of()), "j-1");
    private static final SourceDelivery PANEL = new SourceDelivery(
            new JudgeScores(Map.of("j-1", 8.0, "j-2", 6.0), Map.of(RESCUED.name(), 2.0)), "j-2");

    @Test
    @DisplayName("F3: con una sola fuente el intento queda esperando la otra, sin puntaje")
    void givenOnlyOneSourceThenTheAttemptAwaitsTheOtherWithoutAScore() {
        Attempt attempt = rescueAttempt();

        assertThat(attempt.receive(SENSORS, RESCUE)).isEqualTo(new MeasurementCheck.Accepted());

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.AWAITING_SOURCES);
        assertThat(attempt.getRevisionHistory()).isEmpty();
        assertThat(attempt.countableScore()).isEmpty();
        assertThat(attempt.getDeliveries()).containsExactly(SENSORS);
    }

    @Test
    @DisplayName("F3: cuando llega la última fuente el intento se puntúa con las dos")
    void givenBothSourcesThenTheAttemptIsScoredWithBoth() {
        Attempt attempt = rescueAttempt();
        attempt.receive(SENSORS, RESCUE);

        attempt.receive(PANEL, RESCUE);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.EVALUATED);
        assertThat(attempt.getFinalScore()).isEqualTo(130.0);
        assertThat(attempt.getOriginalSnapshot().authorOrJudgeId()).isEqualTo("j-2");
        assertThat(attempt.getEventHistory()).extracting(AttemptEvent::eventType)
                .containsExactly("SOURCE_RECEIVED", "SOURCE_RECEIVED", "RESULT_REGISTERED");
    }

    @Test
    @DisplayName("F3: el pendiente se ve: el intento dice qué fuente le falta")
    void givenOneSourceInThenTheAttemptAwaitsTheOther() {
        Attempt attempt = rescueAttempt();
        assertThat(attempt.awaitedSources(RESCUE))
                .containsExactlyInAnyOrder(ResultSource.AUTOMATIC_MEASUREMENTS, ResultSource.JUDGE_PANEL);

        attempt.receive(SENSORS, RESCUE);

        assertThat(attempt.awaitedSources(RESCUE)).containsExactly(ResultSource.JUDGE_PANEL);
        assertThat(attempt.contributionsBySource(RESCUE)).isEmpty();
    }

    @Test
    @DisplayName("F3: la explicación separa lo que aportó cada fuente")
    void givenAScoredMixedAttemptThenEachSourceExplainsItsShare() {
        Attempt attempt = rescueAttempt();
        attempt.receive(SENSORS, RESCUE);
        attempt.receive(PANEL, RESCUE);

        assertThat(attempt.awaitedSources(RESCUE)).isEmpty();
        assertThat(attempt.contributionsBySource(RESCUE))
                .extracting(SourceContribution::source, SourceContribution::subtotal)
                .containsExactly(tuple(ResultSource.AUTOMATIC_MEASUREMENTS, 45.0), tuple(ResultSource.JUDGE_PANEL, 85.0));
    }

    @Test
    void givenTheJudgePanelFirstThenTheScoreIsTheSame() {
        Attempt attempt = rescueAttempt();
        attempt.receive(PANEL, RESCUE);

        attempt.receive(SENSORS, RESCUE);

        assertThat(attempt.getFinalScore()).isEqualTo(130.0);
        assertThat(attempt.getLatestMetrics().timeTakenSeconds()).isEqualTo(90.0);
        assertThat(attempt.getLatestMetrics().judgeSubjectiveScores()).containsOnlyKeys("j-1", "j-2");
    }

    @Test
    void givenARulebookThatOnlyNeedsSensorsThenTheirMeasurementsScoreTheAttempt() {
        Attempt attempt = Attempt.of(identity(), RulebookReference.of("ch-maze", MAZE));

        attempt.receive(new SourceDelivery(new Measurements(new TrackPerformance(50.0, 0, 0), 0.0, Map.of()), "j-1"), MAZE);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.EVALUATED);
        assertThat(attempt.getFinalScore()).isEqualTo(110.0);
    }

    @Test
    @DisplayName("F3: la misma fuente dos veces no pisa la primera")
    void givenTheSameSourceTwiceThenTheSecondIsRefusedAndTheFirstStays() {
        Attempt attempt = rescueAttempt();
        attempt.receive(SENSORS, RESCUE);
        SourceDelivery again = new SourceDelivery(new Measurements(new TrackPerformance(40.0, 4, 0), 0.0, Map.of()), "j-3");

        assertThatThrownBy(() -> attempt.receive(again, RESCUE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("AUTOMATIC_MEASUREMENTS already arrived for attempt slot-1-1");
        assertThat(attempt.getDeliveries()).containsExactly(SENSORS);
    }

    @Test
    void givenAScoredAttemptThenNoMoreResultsAreTaken() {
        Attempt attempt = rescueAttempt();
        attempt.receive(SENSORS, RESCUE);
        attempt.receive(PANEL, RESCUE);

        assertThatThrownBy(() -> attempt.receive(PANEL, RESCUE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("attempt is evaluated: it cannot receive results; "
                        + "corrections go through a fault adjustment or an appeal");
    }

    @Test
    void givenMeasurementsTheRulebookRejectsThenNothingChangesAndTheProblemsComeBack() {
        Attempt attempt = rescueAttempt();
        SourceDelivery tooMany = new SourceDelivery(new JudgeScores(Map.of("j-1", 8.0), Map.of(RESCUED.name(), 7.0)), "j-2");

        MeasurementCheck check = attempt.receive(tooMany, RESCUE);

        assertThat(check).isEqualTo(new MeasurementCheck.Rejected(
                List.of("measurement 'victimas_rescatadas' must be between 0.0 and 4.0: 7.0")));
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SCHEDULED);
        assertThat(attempt.getDeliveries()).isEmpty();
        assertThat(attempt.getEventHistory()).isEmpty();
    }

    @Test
    void givenASourceTheRulebookDoesNotTakeThenItIsRejected() {
        Attempt attempt = Attempt.of(identity(), RulebookReference.of("ch-maze", MAZE));

        MeasurementCheck check = attempt.receive(PANEL, MAZE);

        assertThat(check).isEqualTo(new MeasurementCheck.Rejected(List.of("ch-maze v1 takes no results from JUDGE_PANEL")));
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SCHEDULED);
    }

    @Test
    void givenANegativeJudgeScoreThenThePanelIsRejected() {
        assertThatThrownBy(() -> new JudgeScores(Map.of("j-1", -1.0), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score of judge 'j-1'");
    }

    private static Attempt rescueAttempt() {
        return Attempt.of(identity(), RulebookReference.of("ch-rescue", RESCUE));
    }

    private static AttemptIdentity identity() {
        return new AttemptIdentity(AttemptId.of("slot-1", 1), "r-1", "t-1");
    }
}
