package com.roboleague.usecase;

import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.repository.memory.InMemoryTeamRepository;
import com.roboleague.tournament.*;
import com.roboleague.tournament.eligibility.TeamIneligibleException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.roboleague.support.TestValues.*;
import static com.roboleague.usecase.RegistrationFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterTeamUseCaseTest {
    private final InMemoryEditionRepository editions = new InMemoryEditionRepository();
    private final InMemoryTeamRepository teams = new InMemoryTeamRepository();
    private final RegisterTeamUseCase register = new RegisterTeamUseCase(teams, editions, CLOCK);
    private final Category junior = category("junior", 1, 2, 16, 16, 1500);
    private final Edition edition = edition("ed-1", LocalDate.of(2027, 1, 1), junior);

    RegisterTeamUseCaseTest() { editions.save(edition); }

    @Test
    void eligibleTodayButOverTheAgeLimitAtEditionStartIsRejectedWithoutPartialState() {
        // Age 16 at CLOCK.today(), 17 at the edition's start.
        Team candidate = team("t-1");
        assertThatThrownBy(() -> register.execute(edition.getId(), junior.id(), candidate))
                .isInstanceOfSatisfying(TeamIneligibleException.class,
                        e -> assertThat(e.getViolations()).allMatch(reason -> reason.contains("17 > 16")));
        assertThat(teams.findAll()).isEmpty();
        assertThat(editions.findById(edition.getId()).orElseThrow().getRegistrations()).isEmpty();
        assertThat(edition.getRegistrations()).isEmpty();
    }

    @Test
    void tooYoungTodayButEligibleAtEditionStartIsAcceptedWithExplicitTimes() {
        Team candidate = team("t-1").withMembers(List.of(member("m-1", LocalDate.of(2011, 1, 1))));
        Registration registration = register.execute(edition.getId(), junior.id(), candidate);
        assertThat(registration.teamId()).isEqualTo(candidate.getId());
        assertThat(registration.categoryId()).isEqualTo(junior.id());
        assertThat(registration.referenceDate()).isEqualTo(edition.getStartDate());
        assertThat(registration.registeredAt()).isEqualTo(TIME);
        assertThat(teams.findById(candidate.getId())).containsSame(candidate);
        assertThat(edition.getRegistrations()).isEmpty();
        assertThat(editions.findByTeamId(candidate.getId())).hasSize(1);
    }

    @Test
    void foreignCategoryAndMissingEditionDoNotSaveTheCandidate() {
        Team candidate = team("t-1");
        assertThatThrownBy(() -> register.execute(edition.getId(), CategoryId.of("foreign"), candidate))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not offered");
        assertThatThrownBy(() -> register.execute(EditionId.of("missing"), junior.id(), candidate))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Edition not found");
        assertThat(teams.findAll()).isEmpty();
    }

    @Test
    void reportsAllIndependentEligibilityViolationsBeforeAnyWrite() {
        Team candidate = team("t-1").withMembers(List.of()).withRobot(robot(2000))
                .withDocumentation(new Documentation());
        assertThatThrownBy(() -> register.execute(edition.getId(), junior.id(), candidate))
                .isInstanceOfSatisfying(TeamIneligibleException.class, e -> {
                    assertThat(e.getViolations()).hasSize(3);
                    assertThat(e.getViolations()).anyMatch(v -> v.contains("fewer members"))
                            .anyMatch(v -> v.contains("weight")).anyMatch(v -> v.contains("documentation"));
                });
        assertThat(teams.findAll()).isEmpty();
        assertThat(editions.findById(edition.getId()).orElseThrow().getRegistrations()).isEmpty();
    }

    @Test
    void excessMembersAreRejectedWithoutPartialRegistration() {
        Team candidate = team("t-1").withMembers(List.of(member("a", LocalDate.of(2011, 1, 1)),
                member("b", LocalDate.of(2011, 1, 1)), member("c", LocalDate.of(2011, 1, 1))));
        assertThatThrownBy(() -> register.execute(edition.getId(), junior.id(), candidate))
                .isInstanceOfSatisfying(TeamIneligibleException.class,
                        e -> assertThat(e.getViolations()).anyMatch(v -> v.contains("maximum member count")));
        assertThat(teams.findAll()).isEmpty();
    }

    @Test
    void duplicateRegistrationIsAConflictAndCannotOverwriteCanonicalTeam() {
        Team canonical = team("t-1").withMembers(List.of(member("m-1", LocalDate.of(2011, 1, 1))));
        Registration first = register.execute(edition.getId(), junior.id(), canonical);
        assertThatThrownBy(() -> register.execute(edition.getId(), junior.id(), canonical.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already registered");
        Team maliciousReplacement = canonical.withRobot(robot(9999));
        assertThatThrownBy(() -> register.execute(edition.getId(), junior.id(), maliciousReplacement))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already exists");
        assertThat(teams.findById(canonical.getId())).containsSame(canonical);
        assertThat(editions.findById(edition.getId()).orElseThrow().getRegistrations()).containsExactly(first);
    }

    @Test
    void anotherEditionUsesTheCanonicalTeamAndItsOwnCategoryAndCalendar() {
        Team canonical = team("t-1").withMembers(List.of(member("m-1", LocalDate.of(2011, 1, 1))));
        Registration first = register.execute(edition.getId(), junior.id(), canonical);
        Category senior = category("senior", 1, 3, 17, 20, 2000);
        Edition second = edition("ed-2", LocalDate.of(2028, 1, 1), senior);
        editions.save(second);
        Registration another = register.execute(second.getId(), senior.id(), canonical.getId());
        assertThat(another.referenceDate()).isEqualTo(second.getStartDate());
        assertThat(another.categoryId()).isEqualTo(senior.id());
        assertThat(editions.findById(edition.getId()).orElseThrow().getRegistrations()).containsExactly(first);
        assertThat(teams.findAll()).containsExactly(canonical);
        assertThat(editions.findByTeamId(canonical.getId())).hasSize(2);
        assertThatThrownBy(() -> register.execute(second.getId(), senior.id(), TeamId.of("missing")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Team not found");
    }
}
