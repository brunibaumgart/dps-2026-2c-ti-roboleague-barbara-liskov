package com.roboleague.evaluation;

import static com.roboleague.support.TestValues.*;
import com.roboleague.evaluation.audit.AttemptScoreSnapshot;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.evaluation.rules.JudgeSubjectiveRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.PrecisionRule;
import com.roboleague.evaluation.rules.ResourceConsumptionRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.rules.TimeRuleConfig;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptAuditTrailTest {

    private Rulebook standardPolicy;

    @BeforeEach
    void setUp() {
        TimeBasedRule timeRule = new TimeBasedRule("Tiempo", TimeRuleConfig.of(100.0, 60.0, 1.0, 2.0, 0.0));
        PenaltyRule penaltyRule = new PenaltyRule("Penalizaciones", 10.0);
        standardPolicy = new Rulebook(RulebookVersion.first(),
                ScoringScheme.withoutBonuses(List.of(timeRule), List.of(penaltyRule)),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
    }

    @Test
    @DisplayName("Attempt preserves append-only snapshot history and never overwrites previous scores")
    void preservesAppendOnlySnapshotHistory() {
        Attempt attempt = newAttempt();

        // Initial result: 50s, 0 objectives, 0 penalties => Score: 100 + 10 = 110.0
        attempt.receive(sensors(50.0, 0, 0, "judge-alfa"), standardPolicy, audit());

        assertThat(attempt.getRevisionHistory()).hasSize(1);
        assertThat(attempt.getEventHistory()).hasSize(2); // SourceReceived + ResultRegistered
        assertThat(attempt.getFinalScore()).isEqualTo(110.0);
        assertThat(attempt.getStatus()).isEqualTo(Attempt.AttemptStatus.EVALUATED);

        // Later, video review identifies 2 track infractions (penalties)
        attempt.applyPenaltyAdjustment(2, new AuditNote("judge-beta", "Toque de bordes verificado en camara lenta"),
                standardPolicy, audit());

        // Verify history has grown, not overwritten
        assertThat(attempt.getRevisionHistory()).hasSize(2);
        assertThat(attempt.getEventHistory()).hasSize(4); // ... + PenaltyApplied + ScoreAdjusted
        assertThat(attempt.getStatus()).isEqualTo(Attempt.AttemptStatus.ADJUSTED);

        // Original revision 1 is untouched
        AttemptScoreSnapshot rev1 = attempt.getOriginalSnapshot();
        assertThat(rev1.revisionNumber()).isEqualTo(1);
        assertThat(rev1.authorOrJudgeId()).isEqualTo("judge-alfa");
        assertThat(rev1.metrics().penaltiesCount()).isEqualTo(0);
        assertThat(rev1.breakdown().totalScore()).isEqualTo(110.0);

        // Latest revision 2 reflects adjustment: 110 - (2 * 10) = 90.0
        AttemptScoreSnapshot rev2 = attempt.getLatestSnapshot();
        assertThat(rev2.revisionNumber()).isEqualTo(2);
        assertThat(rev2.authorOrJudgeId()).isEqualTo("judge-beta");
        assertThat(rev2.metrics().penaltiesCount()).isEqualTo(2);
        assertThat(rev2.breakdown().totalScore()).isEqualTo(90.0);
        assertThat(rev2.reason()).contains("Toque de bordes verificado");

        assertThat(attempt.getFinalScore()).isEqualTo(90.0);
    }

    @Test
    @DisplayName("A fault adjustment keeps every other measurement of the capture (issue #5)")
    void faultAdjustmentKeepsTheOtherMeasurements() {
        Metric precision = Metric.sensor("precision");
        Rulebook mixed = new Rulebook(RulebookVersion.first(), new ScoringScheme(
                new MetricSheet(List.of(new MetricDefinition(precision, MeasurementUnit.RATIO, ValueRange.between(0.0, 1.0)))),
                new ScoreRules(List.of(TimeBasedRule.standard(100.0, 60.0), new PrecisionRule("Precisión", precision, 50.0),
                        new JudgeSubjectiveRule("Panel", 1.0)), List.of(),
                        List.of(new PenaltyRule("Faltas", 10.0), new ResourceConsumptionRule("Consumo", 80.0, 0.5))),
                new Unlimited()), new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
        Attempt attempt = newAttempt();
        attempt.receive(new SourceDelivery(new Measurements(new TrackPerformance(50.0, 2, 0), 12.5,
                Map.of("precision", 0.9)), "judge-1"), mixed, audit());
        attempt.receive(new SourceDelivery(new JudgeScores(Map.of("judge-1", 8.0), Map.of()), "judge-1"), mixed, audit());

        attempt.applyPenaltyAdjustment(1, new AuditNote("judge-2", "Falta vista en video"), mixed, audit());

        RawMetrics adjusted = attempt.getLatestMetrics();
        assertThat(adjusted.penaltiesCount()).isEqualTo(1);
        assertThat(adjusted.timeTakenSeconds()).isEqualTo(50.0);
        assertThat(adjusted.objectivesCompleted()).isEqualTo(2);
        assertThat(adjusted.resourceConsumption()).isEqualTo(12.5);
        assertThat(adjusted.judgeSubjectiveScores()).containsExactly(Map.entry("judge-1", 8.0));
        assertThat(adjusted.customMetrics()).containsExactly(Map.entry("precision", 0.9));
    }

    @Test
    @DisplayName("A scored attempt takes no second result: corrections are audited adjustments")
    void cannotReceiveTheResultTwice() {
        Attempt attempt = newAttempt();
        attempt.receive(sensors(50.0, 0, 0, "judge-1"), standardPolicy, audit());

        assertThatThrownBy(() -> attempt.receive(sensors(40.0, 0, 3, "judge-2"), standardPolicy, audit()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot receive results");
        assertThat(attempt.getFinalScore()).isEqualTo(110.0);
    }

    @Test
    @DisplayName("Disqualification is audited and a rejected appeal restores the previous status")
    void disqualificationAndRejectedAppealAreTracked() {
        Attempt attempt = newAttempt();
        attempt.receive(sensors(50.0, 3, 1, "judge-1"), standardPolicy, audit());

        attempt.markUnderAppeal();
        assertThat(attempt.getStatus()).isEqualTo(Attempt.AttemptStatus.UNDER_APPEAL);

        attempt.restoreAfterRejectedAppeal();
        assertThat(attempt.getStatus()).isEqualTo(Attempt.AttemptStatus.EVALUATED);
        assertThat(attempt.getRevisionHistory()).hasSize(1);

        attempt.disqualify("Robot abandono la pista", "judge-2", audit());
        assertThat(attempt.getStatus()).isEqualTo(Attempt.AttemptStatus.DISQUALIFIED);
        assertThat(attempt.getEventHistory())
                .last()
                .satisfies(event -> {
                    assertThat(event.eventType()).isEqualTo("ATTEMPT_DISQUALIFIED");
                    assertThat(event.description()).contains("judge-2").contains("Robot abandono la pista");
                });

        assertThatThrownBy(attempt::markUnderAppeal)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disqualified");
    }

    private static SourceDelivery sensors(double seconds, int objectives, int faults, String judgeId) {
        return new SourceDelivery(new Measurements(new TrackPerformance(seconds, objectives, faults), 0.0, Map.of()),
                judgeId);
    }

    private static Attempt newAttempt() {
        return Attempt.of(new AttemptIdentity(AttemptId.of("slot-1", 1), "round-1", "team-1"),
                new RulebookReference("ch-maze", RulebookVersion.first()));
    }
}
