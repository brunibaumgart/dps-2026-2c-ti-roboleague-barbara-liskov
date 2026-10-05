package com.roboleague.tournament;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.rules.TimeRuleConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChallengeTest {

    private final RawMetrics tenSecondsUnderTarget = RawMetrics.of(50.0, 0, 0);

    private Challenge mazeWithBase(double basePoints) {
        return Challenge.draft(ChallengeId.of("ch-maze"), "ed-2026", "Laberinto")
                .publish(List.of(timeRuleWithBase(basePoints)));
    }

    private static TimeBasedRule timeRuleWithBase(double basePoints) {
        return new TimeBasedRule("Tiempo", TimeRuleConfig.of(basePoints, 60.0, 1.0, 1.0, 0.0));
    }

    @Test
    @DisplayName("Un desafío nace al publicar su primer reglamento, que es la versión 1")
    void givenADraftWhenItPublishesThenTheChallengeStartsAtVersionOne() {
        Challenge maze = mazeWithBase(100.0);

        assertThat(maze.currentRulebook().version()).isEqualTo(RulebookVersion.first());
    }

    @Test
    @DisplayName("Cada publicación crea la versión siguiente, numerada por el desafío")
    void givenASecondPublicationThenTheCurrentVersionIsTwo() {
        Challenge maze = mazeWithBase(100.0);

        Rulebook second = maze.publish(List.of(timeRuleWithBase(150.0)));

        assertThat(second.version()).isEqualTo(new RulebookVersion(2));
        assertThat(maze.currentRulebook()).isSameAs(second);
    }

    @Test
    @DisplayName("Publicar una versión nueva no cambia cómo puntúa la anterior")
    void givenANewVersionThenThePreviousOneStillScoresAsBefore() {
        Challenge maze = mazeWithBase(100.0);
        double v1ScoreBefore = maze.currentRulebook().evaluate(tenSecondsUnderTarget).totalScore();

        maze.publish(List.of(timeRuleWithBase(150.0)));

        Rulebook v1 = maze.rulebook(new RulebookVersion(1)).orElseThrow();
        Rulebook v2 = maze.rulebook(new RulebookVersion(2)).orElseThrow();
        assertThat(v1.evaluate(tenSecondsUnderTarget).totalScore()).isEqualTo(v1ScoreBefore).isEqualTo(110.0);
        assertThat(v2.evaluate(tenSecondsUnderTarget).totalScore()).isEqualTo(160.0);
    }

    @Test
    @DisplayName("Un reglamento sin reglas no se puede publicar")
    void givenNoRulesThenTheRulebookIsRejected() {
        Challenge.Draft draft = Challenge.draft(ChallengeId.of("ch-maze"), "ed-2026", "Laberinto");

        assertThatThrownBy(() -> draft.publish(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Pedir una versión que el desafío no publicó no devuelve nada")
    void givenAnUnpublishedVersionThenNoRulebookIsFound() {
        Challenge maze = mazeWithBase(100.0);

        assertThat(maze.rulebook(new RulebookVersion(2))).isEmpty();
    }
}
