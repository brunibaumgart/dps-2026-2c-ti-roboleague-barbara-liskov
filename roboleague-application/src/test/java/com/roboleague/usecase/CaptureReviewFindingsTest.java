package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.AttemptIdentity;
import com.roboleague.evaluation.MetricSheet;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreRules;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.Unlimited;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.repository.memory.InMemoryAttemptRepository;
import com.roboleague.repository.memory.InMemoryChallengeRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del informe de la Entrega 1 sobre la captura, invertidos para describir el comportamiento correcto.
 */
class CaptureReviewFindingsTest {

    private static final ChallengeId MAZE = ChallengeId.of("ch-maze");
    private static final AttemptIdentity TURN = new AttemptIdentity(AttemptId.of("slot-1", 1), "r-1", "t-1");

    private InMemoryAttemptRepository attempts;
    private CaptureAttemptResultUseCase capture;

    @BeforeEach
    void setUp() {
        attempts = new InMemoryAttemptRepository();
        InMemoryChallengeRepository challenges = new InMemoryChallengeRepository();
        challenges.save(Challenge.draft(MAZE, "ed-1", "Laberinto").publish(
                new ScoringScheme(MetricSheet.none(), new ScoreRules(
                        List.of(TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.0, 2.0, 0.0)),
                        List.of(),
                        List.of(new PenaltyRule("Faltas", 10.0))), new Unlimited()),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal()))));
        capture = new CaptureAttemptResultUseCase(attempts, challenges);
    }

    @Test
    @DisplayName("Hallazgo 4: capturar dos veces el mismo turno no pisa el resultado original")
    void givenACapturedTurnThenASecondCaptureIsRejectedAndTheFirstOneStays() {
        capture.execute(new CaptureAttemptResultCommand(MAZE, TURN, RawMetrics.of(50.0, 0, 0), "judge-1"));

        assertThatThrownBy(() -> capture.execute(
                new CaptureAttemptResultCommand(MAZE, TURN, RawMetrics.of(40.0, 0, 5), "judge-2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already captured");

        Attempt stored = attempts.findById(TURN.id()).orElseThrow();
        assertThat(stored.getRevisionHistory()).singleElement()
                .satisfies(revision -> assertThat(revision.authorOrJudgeId()).isEqualTo("judge-1"));
        assertThat(stored.getFinalScore()).isEqualTo(110.0);
    }

    @Test
    @DisplayName("Hallazgo 4: la identidad del intento sale del turno, no la elige quien captura")
    void givenTheSameSlotAndNumberThenTheAttemptIdIsTheSame() {
        assertThat(AttemptId.of("slot-1", 1)).isEqualTo(TURN.id());
        assertThat(AttemptId.of("slot-1", 2)).isNotEqualTo(TURN.id());
    }
}
