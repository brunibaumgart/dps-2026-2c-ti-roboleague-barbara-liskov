package com.roboleague.evaluation;

import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del informe de la Entrega 1 sobre el intento, invertidos para describir el comportamiento correcto.
 */
class AttemptReviewFindingsTest {

    private static final Rulebook RULEBOOK = new Rulebook(RulebookVersion.first(),
            ScoringScheme.withoutBonuses(List.of(TimeBasedRule.of("Tiempo", 100.0, 60.0, 1.0, 2.0, 0.0)),
                    List.of(new PenaltyRule("Faltas", 10.0))),
            new RankingScheme(new AllRounds(), List.of(new HigherTotal())));

    @Test
    @DisplayName("Hallazgo 3: ningún método del intento recibe un puntaje; lo calcula él con su reglamento")
    void givenTheAttemptThenNoPublicMethodTakesAScore() {
        List<String> takingAScore = Arrays.stream(Attempt.class.getMethods())
                .filter(method -> method.getDeclaringClass() == Attempt.class)
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> Arrays.asList(method.getParameterTypes()).contains(ScoreBreakdown.class))
                .map(Method::getName)
                .toList();

        assertThat(takingAScore).isEmpty();
    }

    @Test
    @DisplayName("Hallazgo 3: una apelación aceptada trae métricas corregidas y el intento las puntúa")
    void givenAnAcceptedAppealThenTheAttemptScoresTheCorrectedMetrics() {
        Attempt attempt = Attempt.of(new AttemptIdentity(AttemptId.of("slot-1", 1), "r-1", "t-1"));
        attempt.registerInitialResult(RawMetrics.of(50.0, 0, 4), "judge-1", RULEBOOK);
        attempt.markUnderAppeal();

        attempt.adjustAfterAppeal(new AppealRevision("app-1", RawMetrics.of(50.0, 0, 0),
                new AuditNote("arb-1", "Las faltas no existieron")), RULEBOOK);

        assertThat(attempt.getOriginalSnapshot().breakdown().totalScore()).isEqualTo(70.0);
        assertThat(attempt.getFinalScore()).isEqualTo(110.0);
        assertThat(attempt.getLatestSnapshot().authorOrJudgeId()).isEqualTo("arb-1");
    }
}
