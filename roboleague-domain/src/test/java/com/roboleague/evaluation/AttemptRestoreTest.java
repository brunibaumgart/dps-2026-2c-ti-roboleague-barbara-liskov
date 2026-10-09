package com.roboleague.evaluation;

import com.roboleague.evaluation.Attempt.AttemptStatus;
import com.roboleague.evaluation.audit.AuditTrail;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.roboleague.support.TestValues.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptRestoreTest {

    private static final Rulebook MAZE = new Rulebook(RulebookVersion.first(),
            ScoringScheme.withoutBonuses(List.of(TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.0, 2.0, 0.0)), List.of()),
            new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
    private static final AttemptIdentity IDENTITY = new AttemptIdentity(AttemptId.of(SlotId.of("slot-1"), 1), RoundId.of("r-1"), TeamId.of("t-1"));
    private static final RulebookReference REFERENCE = RulebookReference.of(ChallengeId.of("ch-maze"), MAZE);

    @Test
    void givenAnAttemptWithTwoOpenAppealsThenItsStageCountsThemAndRemembersWhereItGoesBack() {
        Attempt attempt = scored();
        attempt.markUnderAppeal();
        attempt.markUnderAppeal();

        assertThat(attempt.getStage()).isEqualTo(new AttemptStage(AttemptStatus.UNDER_APPEAL, 2, AttemptStatus.EVALUATED));
    }

    @Test
    void givenAStoredAttemptThenTheRestoredOneKeepsItsHistoryAndBehavesAsBefore() {
        Attempt original = scored();
        original.markUnderAppeal();
        original.markUnderAppeal();

        Attempt restored = Attempt.restore(IDENTITY, REFERENCE, new AttemptProgress(original.getStage(),
                original.getDeliveries(), new AuditTrail(original.getRevisionHistory(), original.getEventHistory())));

        assertThat(restored.getStatus()).isEqualTo(AttemptStatus.UNDER_APPEAL);
        assertThat(restored.getDeliveries()).isEqualTo(original.getDeliveries());
        assertThat(restored.getRevisionHistory()).isEqualTo(original.getRevisionHistory());
        assertThat(restored.getEventHistory()).isEqualTo(original.getEventHistory());
        restored.restoreAfterRejectedAppeal();
        assertThat(restored.getStatus()).isEqualTo(AttemptStatus.UNDER_APPEAL);
        restored.restoreAfterRejectedAppeal();
        assertThat(restored.getStatus()).isEqualTo(AttemptStatus.EVALUATED);
    }

    @Test
    void givenAStageUnderAppealWithoutWhereToGoBackThenItCannotBeRestored() {
        AttemptProgress progress = new AttemptProgress(new AttemptStage(AttemptStatus.UNDER_APPEAL, 1, null),
                List.of(), new AuditTrail(List.of(), List.of()));

        assertThatThrownBy(() -> Attempt.restore(IDENTITY, REFERENCE, progress))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("goes back to evaluated or adjusted");
    }

    private static Attempt scored() {
        Attempt attempt = Attempt.of(IDENTITY, REFERENCE);
        attempt.receive(new SourceDelivery(new Measurements(new TrackPerformance(50.0, 0, 0), 0.0, Map.of()), JudgeId.of("j-1")),
                MAZE, audit());
        return attempt;
    }
}
