package com.roboleague.tournament;

import com.roboleague.evaluation.Points;
import com.roboleague.support.TestValues;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.rules.TimeRuleConfig;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.BestNOfM;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChallengeTest {

    private final RawMetrics tenSecondsUnderTarget = TestValues.metrics(50.0, 0, 0);
    private final RankingScheme allRounds = new RankingScheme(new AllRounds(), List.of(new HigherTotal()));

    private Challenge mazeWithBase(double basePoints) {
        return Challenge.draft(ChallengeId.of("ch-maze"), EditionId.of("ed-2026"), "Laberinto")
                .publish(ScoringScheme.withoutBonuses(List.of(timeRuleWithBase(basePoints)), List.of()), allRounds);
    }

    private static TimeBasedRule timeRuleWithBase(double basePoints) {
        return new TimeBasedRule("Tiempo", TimeRuleConfig.of(Points.of(basePoints), 60.0, 1.0, 1.0, Points.ZERO));
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

        Rulebook second = maze.publish(ScoringScheme.withoutBonuses(List.of(timeRuleWithBase(150.0)), List.of()), allRounds);

        assertThat(second.version()).isEqualTo(new RulebookVersion(2));
        assertThat(maze.currentRulebook()).isSameAs(second);
    }

    @Test
    @DisplayName("Publicar una versión nueva no cambia cómo puntúa la anterior")
    void givenANewVersionThenThePreviousOneStillScoresAsBefore() {
        Challenge maze = mazeWithBase(100.0);
        Points v1ScoreBefore = maze.currentRulebook().evaluate(tenSecondsUnderTarget).totalScore();

        maze.publish(ScoringScheme.withoutBonuses(List.of(timeRuleWithBase(150.0)), List.of()), allRounds);

        Rulebook v1 = maze.rulebook(new RulebookVersion(1)).orElseThrow();
        Rulebook v2 = maze.rulebook(new RulebookVersion(2)).orElseThrow();
        assertThat(v1.evaluate(tenSecondsUnderTarget).totalScore()).isEqualTo(v1ScoreBefore).isEqualTo(Points.of(110));
        assertThat(v2.evaluate(tenSecondsUnderTarget).totalScore()).isEqualTo(Points.of(160));
    }

    @Test
    @DisplayName("Pedir una versión que el desafío no publicó no devuelve nada")
    void givenAnUnpublishedVersionThenNoRulebookIsFound() {
        Challenge maze = mazeWithBase(100.0);

        assertThat(maze.rulebook(new RulebookVersion(2))).isEmpty();
    }

    @Test
    @DisplayName("Un reglamento sin reglas no se puede publicar")
    void givenNoRulesThenTheRulebookIsRejected() {
        Challenge.Draft draft = Challenge.draft(ChallengeId.of("ch-maze"), EditionId.of("ed-2026"), "Laberinto");

        assertThatThrownBy(() -> draft.publish(ScoringScheme.withoutBonuses(List.of(), List.of()), allRounds))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un desafío guardado se restaura con todas sus versiones y sigue publicando")
    void givenStoredRulebooksThenTheRestoredChallengeKeepsThemAndPublishesTheNext() {
        Challenge original = mazeWithBase(100.0);
        original.publish(ScoringScheme.withoutBonuses(List.of(timeRuleWithBase(150.0)), List.of()), allRounds);

        Challenge restored = Challenge.restore(Challenge.draft(original.getId(), original.getEditionId(),
                original.getName()), List.of(original.rulebook(RulebookVersion.first()).orElseThrow(),
                original.currentRulebook()));
        Rulebook third = restored.publish(ScoringScheme.withoutBonuses(List.of(timeRuleWithBase(200.0)), List.of()), allRounds);

        assertThat(restored.rulebook(RulebookVersion.first()).orElseThrow().evaluate(tenSecondsUnderTarget).totalScore())
                .isEqualTo(Points.of(110));
        assertThat(third.version()).isEqualTo(new RulebookVersion(3));
    }

    @Test
    @DisplayName("Una categoría sin resultados juega con la versión vigente")
    void givenACategoryWithoutResultsThenItPlaysWithTheCurrentVersion() {
        Challenge maze = mazeWithBase(100.0);
        Rulebook second = maze.publish(ScoringScheme.withoutBonuses(List.of(timeRuleWithBase(150.0)), List.of()), allRounds);

        assertThat(maze.rulebookInForce(List.of())).isSameAs(second);
    }

    @Test
    @DisplayName("Una categoría con resultados sigue con la versión que los puntuó aunque se publique otra")
    void givenACategoryWithResultsThenItKeepsTheVersionThatScoredThem() {
        Challenge maze = mazeWithBase(100.0);
        Rulebook first = maze.currentRulebook();
        maze.publish(ScoringScheme.withoutBonuses(List.of(timeRuleWithBase(150.0)), List.of()), allRounds);

        assertThat(maze.rulebookInForce(List.of(RulebookVersion.first(), RulebookVersion.first()))).isSameAs(first);
    }

    @Test
    @DisplayName("Resultados de una categoría con dos versiones, o con una que no existe, son un dato inconsistente")
    void givenResultsWithSeveralOrUnknownVersionsThenThereIsNoRulebookInForce() {
        Challenge maze = mazeWithBase(100.0);
        maze.publish(ScoringScheme.withoutBonuses(List.of(timeRuleWithBase(150.0)), List.of()), allRounds);

        assertThatThrownBy(() -> maze.rulebookInForce(List.of(RulebookVersion.first(), new RulebookVersion(2))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("results of one category of ch-maze were scored with several rulebook versions");
        assertThatThrownBy(() -> maze.rulebookInForce(List.of(new RulebookVersion(5))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("results of ch-maze were scored with v5, which it never published");
    }

    @Test
    @DisplayName("Restaurar versiones salteadas es un dato corrupto")
    void givenStoredVersionsThatSkipOneThenRestoringFails() {
        Challenge original = mazeWithBase(100.0);
        Rulebook v2 = original.publish(ScoringScheme.withoutBonuses(List.of(timeRuleWithBase(150.0)), List.of()), allRounds);
        Challenge.Draft draft = Challenge.draft(ChallengeId.of("ch-maze"), EditionId.of("ed-2026"), "Laberinto");

        assertThatThrownBy(() -> Challenge.restore(draft, List.of(v2))).isInstanceOf(IllegalStateException.class)
                .hasMessage("stored rulebooks of ch-maze skip version v1");
        assertThatThrownBy(() -> Challenge.restore(draft, List.of())).isInstanceOf(IllegalStateException.class)
                .hasMessage("a stored challenge has at least one rulebook: ch-maze");
    }

    @Test
    @DisplayName("El reglamento declara cómo se clasifica a los equipos")
    void givenAPublishedRulebookThenItExposesItsRankingScheme() {
        RankingScheme bestTwoOfThree = new RankingScheme(new BestNOfM(2, 3), List.of(new HigherTotal()));

        Challenge maze = Challenge.draft(ChallengeId.of("ch-maze"), EditionId.of("ed-2026"), "Laberinto")
                .publish(ScoringScheme.withoutBonuses(List.of(timeRuleWithBase(100.0)), List.of()), bestTwoOfThree);

        assertThat(maze.currentRulebook().rankingScheme()).isSameAs(bestTwoOfThree);
    }

    @Test
    @DisplayName("Pasar a mejores N de M es otra versión y no cambia cómo puntúa cada intento")
    void givenANewVersionWithAnotherRoundSelectionThenAttemptsScoreTheSame() {
        Challenge maze = mazeWithBase(100.0);
        RankingScheme bestOneOfTwo = new RankingScheme(new BestNOfM(1, 2), List.of(new HigherTotal()));

        Rulebook v2 = maze.publish(ScoringScheme.withoutBonuses(List.of(timeRuleWithBase(100.0)), List.of()), bestOneOfTwo);

        Rulebook v1 = maze.rulebook(RulebookVersion.first()).orElseThrow();
        assertThat(v2.evaluate(tenSecondsUnderTarget)).isEqualTo(v1.evaluate(tenSecondsUnderTarget));
        assertThat(v1.rankingScheme().roundSelection()).isInstanceOf(AllRounds.class);
        assertThat(v2.rankingScheme().roundSelection()).isEqualTo(new BestNOfM(1, 2));
    }

    @ParameterizedTest(name = "edición \"{0}\", nombre \"{1}\"")
    @CsvSource({"'  ', Laberinto, edition id cannot be blank", "ed-2026, '  ', challenge name cannot be blank"})
    @DisplayName("Un desafío no acepta edición ni nombre en blanco")
    void givenABlankEditionOrNameThenTheDraftIsRejected(String editionId, String name, String problem) {
        assertThatThrownBy(() -> Challenge.draft(ChallengeId.of("ch-maze"), EditionId.of(editionId), name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(problem);
    }

    @Test
    @DisplayName("Un desafío acepta hasta 120 caracteres de nombre, el tamaño de su columna")
    void givenANameLongerThanItsColumnThenTheDraftIsRejected() {
        EditionId edition = EditionId.of("ed-2026");
        assertThat(Challenge.draft(ChallengeId.of("ch-maze"), edition, "L".repeat(120)).name()).hasSize(120);
        assertThatThrownBy(() -> Challenge.draft(ChallengeId.of("ch-maze"), edition, "L".repeat(121)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("challenge name cannot be longer than 120 characters");
    }
}
