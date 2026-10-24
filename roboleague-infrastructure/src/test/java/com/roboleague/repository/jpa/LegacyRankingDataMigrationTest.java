package com.roboleague.repository.jpa;

import com.roboleague.PostgresContainer;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.RuleCatalog;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.BestAttempt;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.ranking.Placement;
import com.roboleague.ranking.RoundResult;
import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsEntry;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.StandingsTable;
import com.roboleague.ranking.TeamIdentity;
import com.roboleague.ranking.TeamRounds;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;

import static com.roboleague.support.TestValues.TIME;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * V10 completes rulebooks and standings stored before a ranking scheme said which attempt of a turn counts and
 * before a table recorded its rulebook. The context starts at V9; each row is written as the old code wrote it
 * (the new fields removed), then V10 runs and the adapters read it back.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.target=9"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainer.class, JpaChallengeRepository.class, JpaStandingsRepository.class,
        LegacyRankingDataMigrationTest.Catalog.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LegacyRankingDataMigrationTest {

    private static final ChallengeId CHALLENGE = ChallengeId.of("ch-legacy");
    private static final AttemptId COUNTED = AttemptId.of(SlotId.of("slot-legacy"), 1);

    @Autowired
    private JpaChallengeRepository challenges;
    @Autowired
    private JpaStandingsRepository standings;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;

    @TestConfiguration(proxyBeanMethods = false)
    static class Catalog {
        @Bean
        RuleCatalog ruleCatalog() {
            return RuleCatalog.standard();
        }
    }

    @Test
    @DisplayName("Old rulebooks admit one attempt per turn and old tables say which rulebook ranked them")
    void givenRowsStoredBeforeV10ThenTheyAreReadWithTheirDefaults() {
        Challenge maze = Challenge.draft(CHALLENGE, EditionId.of("ed-legacy"), "Laberinto").publish(
                ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", Points.of(20))), List.of()),
                new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
        maze.publish(maze.currentRulebook().scoring(), maze.currentRulebook().rankingScheme());
        challenges.save(maze);
        StandingsId id = new StandingsId(CHALLENGE, CategoryId.of("cat-legacy"));
        standings.save(Standings.first(id, table(), TIME));
        jdbc.update("UPDATE challenges SET rulebooks = rulebooks #- '{versions,0,ranking,attemptSelection}'"
                + " #- '{versions,1,ranking,attemptSelection}' WHERE id = ?", CHALLENGE.value());
        jdbc.update("UPDATE standings SET versions = versions #- '{0,rulebookVersion}' #- '{0,entries,0,attemptRule}'"
                + " #- '{0,entries,0,considered,0,otherAttempts}' WHERE id = ?", id.value());
        assertThat(jdbc.queryForObject("SELECT jsonb_exists(versions -> 0, 'rulebookVersion') FROM standings WHERE id = ?",
                Boolean.class, id.value())).isFalse();

        Flyway.configure().dataSource(dataSource).load().migrate();

        Challenge migrated = challenges.findById(CHALLENGE).orElseThrow();
        assertThat(migrated.rulebooks()).extracting(rulebook -> rulebook.rankingScheme().attemptSelection())
                .containsExactly(BestAttempt.single(), BestAttempt.single());
        StandingsEntry entry = standings.findById(id).orElseThrow().latest().table().entries().getFirst();
        assertThat(standings.findById(id).orElseThrow().latest().table().rulebookVersion())
                .isEqualTo(new RulebookVersion(2));
        assertThat(entry.rounds().attemptRule()).isEqualTo("el mejor intento de cada turno");
        assertThat(entry.rounds().considered()).containsExactly(RoundResult.single(RoundId.of("r-1"), COUNTED,
                Points.of(40)));
    }

    private static StandingsTable table() {
        return new StandingsTable(new RulebookVersion(2), List.of(new StandingsEntry(new Placement(1, "Primer puesto"),
                TeamIdentity.of(TeamId.of("t-a"), "Alpha"), new TeamRounds(
                List.of(RoundResult.single(RoundId.of("r-1"), COUNTED, Points.of(40))), List.of(),
                "todas las rondas", "1 intento por turno"))));
    }
}
