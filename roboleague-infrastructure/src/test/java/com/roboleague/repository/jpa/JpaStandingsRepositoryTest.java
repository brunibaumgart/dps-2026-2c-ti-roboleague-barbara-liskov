package com.roboleague.repository.jpa;

import com.roboleague.PostgresContainer;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.ranking.Placement;
import com.roboleague.ranking.PublicationCheck;
import com.roboleague.ranking.RoundResult;
import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsEntry;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.StandingsTable;
import com.roboleague.ranking.StandingsVersion;
import com.roboleague.ranking.TeamIdentity;
import com.roboleague.ranking.TeamRounds;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.roboleague.support.TestValues.TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainer.class, JpaStandingsRepository.class})
class JpaStandingsRepositoryTest {

    private static final AuditNote CLOSING = AuditNote.of(ActorId.of("org-1"), "Cierre de la fecha");

    @Autowired
    private JpaStandingsRepository repository;

    @Test
    @DisplayName("Standings come back with every version, its rows and rounds, and every publication")
    void standingsRoundTrip() {
        StandingsId id = id("ch-trip");
        Standings standings = Standings.first(id, table(180.0), TIME);
        standings.publish(1, clean(table(180.0)), CLOSING, TIME.plusMinutes(5));
        standings.recalculate(table(200.0), TIME.plusHours(1));
        repository.save(standings);

        Standings stored = repository.findById(id).orElseThrow();

        assertThat(stored.versions()).isEqualTo(standings.versions());
        assertThat(stored.publications()).isEqualTo(standings.publications());
        assertThat(stored.statusOf(stored.version(1).orElseThrow())).isEqualTo(StandingsVersion.Status.OFFICIAL);
        assertThat(stored.latest().table().entries().getFirst().rounds().discarded()).singleElement()
                .satisfies(round -> assertThat(round.attemptId()).isEqualTo(AttemptId.of(SlotId.of("slot-r1-a"), 1)));
        assertThat(repository.findById(id("ch-none"))).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Two recalculations over the same stored standings: the second save is refused instead of losing the first")
    void givenTwoCopiesOfTheSameStandingsThenTheSecondSaveIsRefused() {
        StandingsId id = id("ch-race");
        repository.save(Standings.first(id, table(100.0), TIME));
        Standings first = repository.findById(id).orElseThrow();
        Standings second = repository.findById(id).orElseThrow();

        first.recalculate(table(120.0), TIME.plusHours(1));
        repository.save(first);
        second.recalculate(table(140.0), TIME.plusHours(1));

        assertThatThrownBy(() -> repository.save(second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("were changed by someone else");
        assertThat(repository.findById(id).orElseThrow().latest().table()).isEqualTo(table(120.0));

        first.recalculate(table(160.0), TIME.plusHours(2));
        repository.save(first);
        assertThat(repository.findById(id).orElseThrow().versions()).hasSize(3);
    }

    private static StandingsId id(String challenge) {
        return new StandingsId(ChallengeId.of(challenge), CategoryId.of("cat-maze"));
    }

    private static StandingsTable table(double best) {
        TeamRounds alpha = new TeamRounds(
                List.of(new RoundResult(RoundId.of("r2"), AttemptId.of(SlotId.of("slot-r2-a"), 1), best)),
                List.of(new RoundResult(RoundId.of("r1"), AttemptId.of(SlotId.of("slot-r1-a"), 1), 20.0)),
                "mejores 1 de 2 rondas");
        TeamRounds beta = new TeamRounds(List.of(), List.of(), "mejores 1 de 2 rondas");
        return new StandingsTable(List.of(
                new StandingsEntry(new Placement(1, "Primer puesto"), TeamIdentity.of(TeamId.of("t-a"), "Alpha"), alpha),
                new StandingsEntry(new Placement(2, "Debajo del puesto 1 por Mayor puntaje total"),
                        TeamIdentity.of(TeamId.of("t-b"), "Beta"), beta)));
    }

    private static PublicationCheck clean(StandingsTable current) {
        return new PublicationCheck(current, 0, 0);
    }
}
