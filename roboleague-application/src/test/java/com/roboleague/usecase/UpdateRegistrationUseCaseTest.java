package com.roboleague.usecase;

import java.util.Optional;
import java.util.Map;
import java.util.List;
import java.time.LocalDate;
import com.roboleague.support.ActorId;
import com.roboleague.port.in.TeamCandidate;
import static com.roboleague.support.TestValues.CLOCK;
import com.roboleague.port.in.NotFoundException;
import com.roboleague.port.in.RegisteredTeam;

import com.roboleague.repository.memory.InMemoryEditionRepository;
import com.roboleague.repository.memory.InMemoryTeamRepository;
import com.roboleague.tournament.*;
import com.roboleague.tournament.eligibility.TeamIneligibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.roboleague.support.TestValues.DATE;
import static com.roboleague.support.TestValues.TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateRegistrationUseCaseTest {
    private final InMemoryEditionRepository editions = new InMemoryEditionRepository();
    private final InMemoryTeamRepository teams = new InMemoryTeamRepository();
    private final UpdateRegistrationUseCase useCase = new UpdateRegistrationUseCase(editions, teams, CLOCK);
    private final Team original = RegistrationFixtures.team("combined-team");
    private final Category small = RegistrationFixtures.category("small", 1, 4, 10, 25, 1500);
    private final Category large = RegistrationFixtures.category("large", 1, 4, 10, 25, 3000);
    private Edition edition;

    @BeforeEach
    void setUp() {
        teams.save(original);
        edition = RegistrationFixtures.edition("combined-edition", DATE, small, large).registerTeam(original, small.id(), TIME);
        editions.save(edition);
    }

    @Test
    void canChangeRobotAndCategoryTogetherWithoutValidatingTheObsoleteCategory() {
        Team candidate = original.withRobot(RegistrationFixtures.robot(2500));
        RegisteredTeam result = useCase.execute(edition.getId(), original.getId(), large.id(), candidate);
        assertThat(result.team()).isSameAs(candidate);
        assertThat(result.registration().categoryId()).isEqualTo(large.id());
        assertThat(result.registration().registeredAt()).isEqualTo(TIME);
        assertThat(result.registration().referenceDate()).isEqualTo(DATE);
        assertThat(teams.findById(original.getId())).containsSame(candidate);
        assertThat(editions.findById(edition.getId()).orElseThrow().registration(original.getId())).contains(result.registration());
        assertThat(edition.registration(original.getId()).orElseThrow().categoryId()).isEqualTo(small.id());
    }

    @Test
    void invalidCandidateInAnotherEditionChangesNeitherTeamNorCategory() {
        Edition other = RegistrationFixtures.edition("other-edition", DATE.plusDays(2), small).registerTeam(original, small.id(), TIME);
        editions.save(other);
        Team candidate = original.withRobot(RegistrationFixtures.robot(2500));
        assertThatThrownBy(() -> useCase.execute(edition.getId(), original.getId(), large.id(), candidate))
                .isInstanceOf(TeamIneligibleException.class)
                .satisfies(error -> assertThat(((TeamIneligibleException) error).getViolations())
                        .anyMatch(reason -> reason.contains("other-edition")));
        assertUnchanged();
        assertThat(editions.findById(other.getId())).containsSame(other);
    }

    @Test
    void invalidTargetCategoryChangesNeitherRepository() {
        Team candidate = original.withRobot(RegistrationFixtures.robot(3500));
        assertThatThrownBy(() -> useCase.execute(edition.getId(), original.getId(), large.id(), candidate))
                .isInstanceOf(TeamIneligibleException.class);
        assertUnchanged();
        assertThatThrownBy(() -> useCase.execute(edition.getId(), original.getId(), CategoryId.of("foreign"), original))
                .isInstanceOf(IllegalArgumentException.class);
        assertUnchanged();
    }

    @Test
    void rejectsMissingRegistrationAndInconsistentIdentityBeforeWriting() {
        Edition other = RegistrationFixtures.edition("no-registration", DATE, small);
        editions.save(other);
        assertThatThrownBy(() -> useCase.execute(other.getId(), original.getId(), small.id(), original))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("Registration not found");
        assertThatThrownBy(() -> useCase.execute(edition.getId(), TeamId.of("another-team"), large.id(), original))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("identity");
        assertUnchanged();
    }

    @Test
    void queriesReturnTheCanonicalUpdatedTeamAndPerformNoMutation() {
        Team candidate = original.withProfile(TeamProfile.of(original.getId(), "Updated", "New institution"));
        useCase.execute(edition.getId(), original.getId(), small.id(), candidate);
        var stored = editions.findById(edition.getId()).orElseThrow();
        QueryRegistrationsUseCase query = new QueryRegistrationsUseCase(editions, teams);
        assertThat(query.get(edition.getId(), original.getId()).team().getName()).isEqualTo("Updated");
        assertThat(query.list(edition.getId())).extracting(RegisteredTeam::team).containsExactly(candidate);
        assertThat(editions.findById(edition.getId())).containsSame(stored);
        assertThat(teams.findById(original.getId())).containsSame(candidate);
    }

    @Test
    void emptyQueriesRemainEmptyAndUnknownEditionsAreErrors() {
        QueryEditionsUseCase query = new QueryEditionsUseCase(new InMemoryEditionRepository());
        assertThat(query.list()).isEmpty();
        assertThatThrownBy(() -> query.get(EditionId.of("missing"))).isInstanceOf(NotFoundException.class);
        Edition empty = RegistrationFixtures.edition("empty", DATE, small);
        editions.save(empty);
        QueryRegistrationsUseCase registrations = new QueryRegistrationsUseCase(editions, teams);
        assertThat(registrations.list(empty.getId())).isEmpty();
        assertThatThrownBy(() -> registrations.get(empty.getId(), original.getId())).isInstanceOf(NotFoundException.class);
    }

    private void assertUnchanged() {
        assertThat(teams.findById(original.getId())).containsSame(original);
        assertThat(editions.findById(edition.getId())).containsSame(edition);
    }

    @Test
    void aCandidateIsBuiltWithTheServerClockAndReplacesTheTeamAndCategoryTogether() {
        TeamCandidate heavier = candidate(original.getId().value(), 2500);

        RegisteredTeam result = useCase.execute(edition.getId(), original.getId(), large.id(), heavier);

        assertThat(result.registration().categoryId()).isEqualTo(large.id());
        assertThat(result.team().getRobot().getSpecification().weightGrams()).isEqualTo(2500);
        assertThat(result.team().getDocumentation().getVerifiedAt()).isEqualTo(TIME);
        assertThat(teams.findById(original.getId())).contains(result.team());
    }

    private static TeamCandidate candidate(String id, double robotWeight) {
        return new TeamCandidate(TeamId.of(id), id, "School", RegistrationFixtures.robot(id + "-robot", robotWeight),
                List.of(new TeamCandidate.Member(ParticipantId.of(id + "-m-1"), "Ana", LocalDate.of(2010, 1, 1), "MEMBER")),
                Map.of("CONSENT", "consent.pdf"), Optional.of(ActorId.of("inspector")));
    }
}
