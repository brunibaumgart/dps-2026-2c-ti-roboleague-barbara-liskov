package com.roboleague.repository.jpa;

import com.roboleague.PostgresContainer;
import com.roboleague.evaluation.CappedAt;
import com.roboleague.evaluation.EvaluationFeedback;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.RuleCatalog;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.evaluation.rules.CompositeScoreRule;
import com.roboleague.evaluation.rules.CountedFaultRule;
import com.roboleague.evaluation.rules.FaultTariff;
import com.roboleague.evaluation.rules.Milestone;
import com.roboleague.evaluation.rules.MilestoneBonusRule;
import com.roboleague.evaluation.rules.ObjectiveBonusRule;
import com.roboleague.evaluation.rules.PenaltyRule;
import com.roboleague.evaluation.rules.TimeBasedRule;
import com.roboleague.evaluation.scheme.BestNOfM;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.LowerTime;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainer.class, JpaChallengeRepository.class, JpaChallengeRepositoryTest.Catalog.class})
class JpaChallengeRepositoryTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Catalog {
        @Bean
        RuleCatalog ruleCatalog() {
            return RuleCatalog.standard();
        }
    }

    private static final Metric COLLISIONS = Metric.sensor("colisiones");
    private static final Metric CHECKPOINT = Metric.sensor("checkpoint");
    private static final RankingScheme BEST_THREE_OF_FIVE =
            new RankingScheme(new BestNOfM(3, 5), List.of(new HigherTotal(), new LowerTime()));

    @Autowired
    private JpaChallengeRepository repository;

    private final RawMetrics run = new RawMetrics(new TrackPerformance(50.0, 5, 1),
            EvaluationFeedback.withMeasurements(Map.of(COLLISIONS.name(), 3.0, CHECKPOINT.name(), 1.0)));

    private static ScoringScheme mazeScoring(double cap) {
        return new ScoringScheme(
                List.of(new CompositeScoreRule("Desempeño en pista", List.of(
                                TimeBasedRule.standard(100.0, 60.0), ObjectiveBonusRule.standard(20.0, 5))),
                        new PenaltyRule("Faltas", 15.0),
                        new CountedFaultRule("Colisiones", COLLISIONS, new FaultTariff(1, 5.0))),
                List.of(new MilestoneBonusRule("Checkpoint", new Milestone(CHECKPOINT, 1.0), 30.0)),
                new CappedAt(cap));
    }

    private Challenge mazeWithTwoVersions(String id) {
        Challenge maze = Challenge.draft(ChallengeId.of(id), "ed-2026", "Laberinto")
                .publish(mazeScoring(40.0), BEST_THREE_OF_FIVE);
        maze.publish(mazeScoring(20.0), BEST_THREE_OF_FIVE);
        return maze;
    }

    @Test
    @DisplayName("Un desafío vuelve de Postgres con todas las versiones de su reglamento")
    void givenAChallengeWithTwoVersionsThenBothComeBackWithTheirDefinitions() {
        Challenge maze = mazeWithTwoVersions("ch-repo-1");

        repository.save(maze);
        Challenge stored = repository.findById(ChallengeId.of("ch-repo-1")).orElseThrow();

        assertThat(stored.getEditionId()).isEqualTo("ed-2026");
        assertThat(stored.getName()).isEqualTo("Laberinto");
        assertThat(stored.currentRulebook().version()).isEqualTo(new RulebookVersion(2));
        assertThat(stored.rulebook(RulebookVersion.first()).orElseThrow().definition())
                .isEqualTo(maze.rulebook(RulebookVersion.first()).orElseThrow().definition());
        assertThat(stored.currentRulebook().definition()).isEqualTo(maze.currentRulebook().definition());
    }

    @Test
    @DisplayName("El reglamento restaurado puntúa igual que el original, tope incluido")
    void givenAStoredChallengeThenEachVersionScoresExactlyAsBefore() {
        Challenge maze = mazeWithTwoVersions("ch-repo-2");

        repository.save(maze);
        Challenge stored = repository.findById(ChallengeId.of("ch-repo-2")).orElseThrow();

        for (RulebookVersion version : List.of(RulebookVersion.first(), new RulebookVersion(2))) {
            Rulebook original = maze.rulebook(version).orElseThrow();
            Rulebook restored = stored.rulebook(version).orElseThrow();
            assertThat(restored.evaluate(run)).isEqualTo(original.evaluate(run));
        }
        assertThat(stored.currentRulebook().evaluate(run).items())
                .anyMatch(item -> item.concept().equals("Tope de bonificaciones") && item.subtotal() == -10.0);
        assertThat(stored.currentRulebook().rankingScheme().roundSelection()).isEqualTo(new BestNOfM(3, 5));
    }

    @Test
    @DisplayName("Un desafío restaurado publica la versión siguiente y se vuelve a guardar")
    void givenARestoredChallengeThenItPublishesVersionThreeAndIsSavedAgain() {
        repository.save(mazeWithTwoVersions("ch-repo-3"));
        Challenge stored = repository.findById(ChallengeId.of("ch-repo-3")).orElseThrow();

        stored.publish(mazeScoring(60.0), BEST_THREE_OF_FIVE);
        repository.save(stored);

        Challenge reloaded = repository.findById(ChallengeId.of("ch-repo-3")).orElseThrow();
        assertThat(reloaded.currentRulebook().version()).isEqualTo(new RulebookVersion(3));
        assertThat(reloaded.rulebook(RulebookVersion.first())).isPresent();
    }

    @Test
    @DisplayName("Buscar un desafío que no existe no devuelve nada")
    void givenAnUnknownIdThenNothingIsFound() {
        assertThat(repository.findById(ChallengeId.of("ch-none"))).isEmpty();
    }
}
