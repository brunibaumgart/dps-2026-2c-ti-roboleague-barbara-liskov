package com.roboleague.repository.jpa;

import com.roboleague.repository.ConcurrentModificationConflict;
import com.roboleague.PostgresContainer;
import com.roboleague.tournament.Category;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.DateRange;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionContext;
import com.roboleague.tournament.EditionHeader;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.Registration;
import com.roboleague.tournament.Season;
import com.roboleague.tournament.TeamId;
import com.roboleague.tournament.Tournament;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainer.class, JpaEditionRepository.class})
class JpaEditionRepositoryTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 10);
    private static final Category JUNIOR = Category.of(CategoryId.of("cat-junior"), "Junior", 2, 4, 13, 17, 1500.0);
    private static final Category OPEN = Category.of(CategoryId.of("cat-open"), "Abierta", 1, 6, 16, null, 3000.0,
            400.0, 300.0, 250.0);

    @Autowired
    private JpaEditionRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("An edition comes back with its tournament, dates, categories and every registration as stored")
    void editionRoundTrip() {
        Edition edition = edition("ed-trip", List.of(JUNIOR, OPEN),
                registration("ed-trip", "t-b", JUNIOR, LocalDateTime.of(2026, 10, 1, 9, 30, 15)),
                registration("ed-trip", "t-a", OPEN, LocalDateTime.of(2026, 10, 2, 18, 0, 0, 123_000_000)));
        repository.save(edition);
        entityManager.clear();

        Edition stored = repository.findById(EditionId.of("ed-trip")).orElseThrow();

        assertThat(stored.getContext()).isEqualTo(edition.getContext());
        assertThat(stored.getDates()).isEqualTo(edition.getDates());
        assertThat(stored.getCategories()).containsExactly(JUNIOR, OPEN);
        assertThat(stored.category(OPEN.id()).maxAge()).isNull();
        assertThat(stored.getRegistrations())
                .extracting(Registration::teamId, Registration::editionId, Registration::categoryId,
                        Registration::referenceDate, Registration::registeredAt)
                .containsExactly(
                        tuple(TeamId.of("t-b"), EditionId.of("ed-trip"), JUNIOR.id(), START,
                                LocalDateTime.of(2026, 10, 1, 9, 30, 15)),
                        tuple(TeamId.of("t-a"), EditionId.of("ed-trip"), OPEN.id(), START,
                                LocalDateTime.of(2026, 10, 2, 18, 0, 0, 123_000_000)));
        assertThat(repository.findById(EditionId.of("ed-none"))).isEmpty();
    }

    @Test
    @DisplayName("Saving a new version replaces the stored edition instead of adding another")
    void savingANewVersionReplacesTheStoredOne() {
        Edition first = edition("ed-replace", List.of(JUNIOR, OPEN),
                registration("ed-replace", "t-a", JUNIOR, LocalDateTime.of(2026, 10, 1, 9, 0)));
        repository.save(first);
        Edition moved = Edition.restore(first.getContext(), first.getDates(), first.getCategories(),
                List.of(new Registration(TeamId.of("t-a"), first.getId(), OPEN.id(), START,
                        LocalDateTime.of(2026, 10, 1, 9, 0))));
        repository.save(moved);
        entityManager.clear();

        assertThat(repository.findAll()).filteredOn(edition -> edition.getId().equals(first.getId())).singleElement()
                .satisfies(edition -> assertThat(edition.registration(TeamId.of("t-a")).orElseThrow().categoryId())
                        .isEqualTo(OPEN.id()));
    }

    @Test
    @DisplayName("Editions of a team are the ones with its registration, ordered by id")
    void findsEditionsByRegisteredTeam() {
        repository.save(edition("ed-team-b", List.of(JUNIOR),
                registration("ed-team-b", "t-shared", JUNIOR, LocalDateTime.of(2026, 10, 1, 9, 0))));
        repository.save(edition("ed-team-a", List.of(JUNIOR),
                registration("ed-team-a", "t-other", JUNIOR, LocalDateTime.of(2026, 10, 1, 9, 0)),
                registration("ed-team-a", "t-shared", JUNIOR, LocalDateTime.of(2026, 10, 1, 10, 0))));
        repository.save(edition("ed-team-c", List.of(JUNIOR),
                registration("ed-team-c", "t-other", JUNIOR, LocalDateTime.of(2026, 10, 1, 9, 0))));
        entityManager.clear();

        assertThat(repository.findByTeamId(TeamId.of("t-shared"))).extracting(edition -> edition.getId().value())
                .containsExactly("ed-team-a", "ed-team-b");
        assertThat(repository.findByTeamId(TeamId.of("t-nobody"))).isEmpty();
        assertThat(repository.findAll()).extracting(edition -> edition.getId().value())
                .containsSubsequence("ed-team-a", "ed-team-b", "ed-team-c");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Two registrations over the same stored edition: the second save is refused instead of losing the first")
    void givenAnEditionChangedByAnotherTransactionThenTheStaleSaveIsRefused() {
        EditionId id = EditionId.of("ed-race");
        repository.save(edition("ed-race", List.of(JUNIOR, OPEN)));
        TransactionTemplate useCase = new TransactionTemplate(transactionManager);
        TransactionTemplate other = new TransactionTemplate(transactionManager);
        other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        useCase.executeWithoutResult(status -> {
            Edition loaded = repository.findById(id).orElseThrow();
            other.executeWithoutResult(inner -> repository.save(withRegistration(
                    repository.findById(id).orElseThrow(), "t-first")));
            assertThatThrownBy(() -> repository.save(withRegistration(loaded, "t-second")))
                    .isInstanceOf(ConcurrentModificationConflict.class)
                    .hasMessageContaining("was changed by someone else");
            status.setRollbackOnly();
        });

        assertThat(repository.findById(id).orElseThrow().getRegistrations())
                .extracting(Registration::teamId).containsExactly(TeamId.of("t-first"));
        useCase.executeWithoutResult(status -> {
            Edition loaded = repository.findById(id).orElseThrow();
            Edition second = withRegistration(loaded, "t-second");
            repository.save(second);
            repository.save(withRegistration(second, "t-third"));
        });
        assertThat(repository.findById(id).orElseThrow().getRegistrations()).extracting(Registration::teamId)
                .containsExactly(TeamId.of("t-first"), TeamId.of("t-second"), TeamId.of("t-third"));
    }

    private static Edition withRegistration(Edition edition, String teamId) {
        var registrations = new ArrayList<>(edition.getRegistrations());
        registrations.add(new Registration(TeamId.of(teamId), edition.getId(), JUNIOR.id(), START,
                LocalDateTime.of(2026, 10, 1, 9, registrations.size())));
        return Edition.restore(edition.getContext(), edition.getDates(), edition.getCategories(), registrations);
    }

    private static Edition edition(String id, List<Category> categories, Registration... registrations) {
        Tournament tournament = Tournament.of("tour-1", "Liga Robótica", "Torneo anual",
                new Season("season-2026", 2026, "Temporada 2026"));
        return Edition.restore(new EditionContext(tournament, new EditionHeader(EditionId.of(id), "Edición " + id, 3)),
                new DateRange(START, START.plusDays(2)), categories, List.of(registrations));
    }

    private static Registration registration(String editionId, String teamId, Category category,
                                             LocalDateTime registeredAt) {
        return new Registration(TeamId.of(teamId), EditionId.of(editionId), category.id(), START, registeredAt);
    }
}
