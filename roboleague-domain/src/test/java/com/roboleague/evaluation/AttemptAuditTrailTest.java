package com.roboleague.evaluation;

import com.roboleague.evaluation.audit.AttemptDisqualifiedEvent;
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
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.roboleague.support.TestValues.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptAuditTrailTest {

    private Rulebook standardPolicy;

    @BeforeEach
    void setUp() {
        TimeBasedRule timeRule = new TimeBasedRule("Tiempo", TimeRuleConfig.of(Points.of(100), 60.0, 1.0, 2.0, Points.ZERO));
        PenaltyRule penaltyRule = new PenaltyRule("Penalizaciones", Points.of(10));
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
        assertThat(attempt.getScoreBreakdown().totalScore()).isEqualTo(Points.of(110));
        assertThat(attempt.getStatus()).isEqualTo(Attempt.AttemptStatus.EVALUATED);

        // Later, video review identifies 2 track infractions (penalties)
        attempt.applyPenaltyAdjustment(2, new AuditNote(ActorId.of("judge-beta"), "Toque de bordes verificado en camara lenta"),
                standardPolicy, audit());

        // Verify history has grown, not overwritten
        assertThat(attempt.getRevisionHistory()).hasSize(2);
        assertThat(attempt.getEventHistory()).hasSize(4); // ... + PenaltyApplied + ScoreAdjusted
        assertThat(attempt.getStatus()).isEqualTo(Attempt.AttemptStatus.ADJUSTED);

        // Original revision 1 is untouched
        AttemptScoreSnapshot rev1 = attempt.getRevisionHistory().getFirst();
        assertThat(rev1.revisionNumber()).isEqualTo(1);
        assertThat(rev1.authorOrJudgeId()).isEqualTo(ActorId.of("judge-alfa"));
        assertThat(rev1.metrics().penaltiesCount()).isEqualTo(0);
        assertThat(rev1.breakdown().totalScore()).isEqualTo(Points.of(110));

        // Latest revision 2 reflects adjustment: 110 - (2 * 10) = 90.0
        AttemptScoreSnapshot rev2 = attempt.getLatestSnapshot();
        assertThat(rev2.revisionNumber()).isEqualTo(2);
        assertThat(rev2.authorOrJudgeId()).isEqualTo(ActorId.of("judge-beta"));
        assertThat(rev2.metrics().penaltiesCount()).isEqualTo(2);
        assertThat(rev2.breakdown().totalScore()).isEqualTo(Points.of(90));
        assertThat(rev2.reason()).contains("Toque de bordes verificado");

        assertThat(attempt.getScoreBreakdown().totalScore()).isEqualTo(Points.of(90));
    }

    @Test
    @DisplayName("A fault adjustment keeps every other measurement of the capture (issue #5)")
    void faultAdjustmentKeepsTheOtherMeasurements() {
        Metric precision = Metric.sensor("precision");
        Rulebook mixed = new Rulebook(RulebookVersion.first(), new ScoringScheme(
                new MetricSheet(List.of(new MetricDefinition(precision, MeasurementUnit.RATIO, ValueRange.between(0.0, 1.0)))),
                new ScoreRules(List.of(TimeBasedRule.standard(Points.of(100), 60.0), new PrecisionRule("Precisión", precision, Points.of(50)),
                        new JudgeSubjectiveRule("Panel", 1.0)), List.of(),
                        List.of(new PenaltyRule("Faltas", Points.of(10)), new ResourceConsumptionRule("Consumo", 80.0, 0.5))),
                new Unlimited()), new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
        Attempt attempt = newAttempt();
        attempt.receive(new SourceDelivery(new Measurements(new TrackPerformance(50.0, 2, 0), 12.5,
                Map.of("precision", 0.9)), JudgeId.of("judge-1")), mixed, audit());
        attempt.receive(new SourceDelivery(new JudgeScores(Map.of(JudgeId.of("judge-1"), 8.0), Map.of()), JudgeId.of("judge-1")), mixed, audit());

        attempt.applyPenaltyAdjustment(1, new AuditNote(ActorId.of("judge-2"), "Falta vista en video"), mixed, audit());

        RawMetrics adjusted = attempt.getLatestMetrics();
        assertThat(adjusted.penaltiesCount()).isEqualTo(1);
        assertThat(adjusted.timeTakenSeconds()).isEqualTo(50.0);
        assertThat(adjusted.objectivesCompleted()).isEqualTo(2);
        assertThat(adjusted.resourceConsumption()).isEqualTo(12.5);
        assertThat(adjusted.judgeSubjectiveScores()).containsExactly(Map.entry(JudgeId.of("judge-1"), 8.0));
        assertThat(adjusted.customMetrics()).containsExactly(Map.entry("precision", 0.9));
    }

    @Test
    @DisplayName("A fault adjustment that is not positive is refused before it adds a revision or an event")
    void nonPositiveFaultAdjustmentChangesNothing() {
        Attempt attempt = newAttempt();
        attempt.receive(sensors(50.0, 0, 0, "judge-1"), standardPolicy, audit());
        AuditNote note = new AuditNote(ActorId.of("judge-2"), "Sin faltas");

        assertThatThrownBy(() -> attempt.applyPenaltyAdjustment(0, note, standardPolicy, audit()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("additionalPenalties must be positive");
        assertThatThrownBy(() -> attempt.applyPenaltyAdjustment(-1, note, standardPolicy, audit()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(attempt.getRevisionHistory()).hasSize(1);
        assertThat(attempt.getEventHistory()).hasSize(2);
        assertThat(attempt.getStatus()).isEqualTo(Attempt.AttemptStatus.EVALUATED);
    }

    @Test
    @DisplayName("A disqualification records who decided it and why, and keeps every revision")
    void disqualificationRecordsItsAuthorAndReason() {
        Attempt attempt = newAttempt();
        attempt.receive(sensors(50.0, 0, 0, "judge-1"), standardPolicy, audit());

        attempt.disqualify(AuditNote.of(JudgeId.of("judge-1").asActorId(), "Robot fuera de reglamento"), audit());

        assertThat(attempt.getEventHistory()).last()
                .isInstanceOfSatisfying(AttemptDisqualifiedEvent.class, event -> {
                    assertThat(event.authorId()).isEqualTo(ActorId.of("judge-1"));
                    assertThat(event.reason()).isEqualTo("Robot fuera de reglamento");
                });
        assertThat(attempt.getRevisionHistory()).hasSize(1);
        assertThat(attempt.countableScore()).isEmpty();
        assertThatThrownBy(() -> attempt.disqualify(AuditNote.of(ActorId.of("judge-1"), "otra vez"), audit()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("attempt is disqualified: it cannot be disqualified");
    }

    @Test
    @DisplayName("A scored attempt takes no second result: corrections are audited adjustments")
    void cannotReceiveTheResultTwice() {
        Attempt attempt = newAttempt();
        attempt.receive(sensors(50.0, 0, 0, "judge-1"), standardPolicy, audit());

        assertThatThrownBy(() -> attempt.receive(sensors(40.0, 0, 3, "judge-2"), standardPolicy, audit()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot receive results");
        assertThat(attempt.getScoreBreakdown().totalScore()).isEqualTo(Points.of(110));
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

        attempt.disqualify(AuditNote.of(JudgeId.of("judge-2").asActorId(), "Robot abandono la pista"), audit());
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
                JudgeId.of(judgeId));
    }

    private static Attempt newAttempt() {
        return Attempt.of(new AttemptIdentity(AttemptId.of(SlotId.of("slot-1"), 1), RoundId.of("round-1"), TeamId.of("team-1")),
                new RulebookReference(ChallengeId.of("ch-maze"), RulebookVersion.first()));
    }
}
