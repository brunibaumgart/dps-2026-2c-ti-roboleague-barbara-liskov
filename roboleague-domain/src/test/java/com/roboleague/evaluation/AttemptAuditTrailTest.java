package com.roboleague.evaluation;

import com.roboleague.evaluation.audit.AttemptScoreSnapshot;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.rules.TimeRuleConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptAuditTrailTest {

    private ScoringPolicy standardPolicy;

    @BeforeEach
    void setUp() {
        TimeBasedRule timeRule = new TimeBasedRule("Tiempo", TimeRuleConfig.of(100.0, 60.0, 1.0, 2.0, 0.0));
        PenaltyRule penaltyRule = new PenaltyRule("Penalizaciones", 10.0);
        standardPolicy = ScoringPolicy.of("pol-std", "v1.0", "Reglamento Estandar", List.of(timeRule, penaltyRule));
    }

    @Test
    @DisplayName("Attempt preserves append-only snapshot history and never overwrites previous scores")
    void preservesAppendOnlySnapshotHistory() {
        Attempt attempt = Attempt.of("att-1", "team-1", "slot-1", "round-1", 1);

        // Initial result: 50s, 0 objectives, 0 penalties => Score: 100 + 10 = 110.0
        RawMetrics initialMetrics = RawMetrics.of(50.0, 0, 0);
        ScoreBreakdown initialBreakdown = standardPolicy.evaluate(initialMetrics);

        attempt.registerInitialResult(initialMetrics, initialBreakdown, "judge-alfa");

        assertThat(attempt.getRevisionHistory()).hasSize(1);
        assertThat(attempt.getEventHistory()).hasSize(1);
        assertThat(attempt.getFinalScore()).isEqualTo(110.0);
        assertThat(attempt.getStatus()).isEqualTo(Attempt.AttemptStatus.EVALUATED);

        // Later, video review identifies 2 track infractions (penalties)
        attempt.applyPenaltyAdjustment(2, "Toque de bordes verificado en camara lenta", "judge-beta", standardPolicy);

        // Verify history has grown, not overwritten
        assertThat(attempt.getRevisionHistory()).hasSize(2);
        assertThat(attempt.getEventHistory()).hasSize(3); // ResultRegistered + PenaltyApplied + ScoreAdjusted
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
    @DisplayName("Cannot call registerInitialResult twice on the same attempt")
    void cannotRegisterInitialResultTwice() {
        Attempt attempt = Attempt.of("att-2", "team-1", "slot-1", "round-1", 1);
        RawMetrics metrics = RawMetrics.of(50.0, 0, 0);
        ScoreBreakdown breakdown = standardPolicy.evaluate(metrics);

        attempt.registerInitialResult(metrics, breakdown, "judge-1");

        assertThatThrownBy(() -> attempt.registerInitialResult(metrics, breakdown, "judge-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has registered results");
    }
    @Test
    @DisplayName("Disqualification is audited and a rejected appeal restores the previous status")
    void disqualificationAndRejectedAppealAreTracked() {
        Attempt attempt = Attempt.of("att-3", "team-1", "slot-1", "round-1", 1);
        RawMetrics metrics = RawMetrics.of(50.0, 3, 1);
        attempt.registerInitialResult(metrics, standardPolicy.evaluate(metrics), "judge-1");

        attempt.markUnderAppeal();
        assertThat(attempt.getStatus()).isEqualTo(Attempt.AttemptStatus.UNDER_APPEAL);

        attempt.restoreAfterRejectedAppeal();
        assertThat(attempt.getStatus()).isEqualTo(Attempt.AttemptStatus.EVALUATED);
        assertThat(attempt.getRevisionHistory()).hasSize(1);

        attempt.disqualify("Robot abandono la pista", "judge-2");
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
}
