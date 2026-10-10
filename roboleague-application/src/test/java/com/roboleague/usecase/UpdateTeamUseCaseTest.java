package com.roboleague.usecase;

import com.roboleague.evaluation.ScoringScheme;
import com.roboleague.evaluation.rules.ObjectivesRule;
import com.roboleague.evaluation.scheme.AllRounds;
import com.roboleague.evaluation.scheme.HigherTotal;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.ranking.StandingsId;
import com.roboleague.repository.memory.*;
import com.roboleague.support.ActorId;
import com.roboleague.tournament.*;
import com.roboleague.tournament.eligibility.TeamIneligibleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.roboleague.support.TestValues.*;
import static com.roboleague.usecase.RegistrationFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateTeamUseCaseTest {
    private final InMemoryTeamRepository teams = new InMemoryTeamRepository();
    private final InMemoryEditionRepository editions = new InMemoryEditionRepository();
    private final RegisterTeamUseCase register = new RegisterTeamUseCase(teams, editions, CLOCK);
    private final UpdateTeamUseCase update = new UpdateTeamUseCase(teams, editions);
    private final ChangeRegistrationCategoryUseCase changeCategory = new ChangeRegistrationCategoryUseCase(teams, editions);
    private final Category broad = category("broad", 1, 4, 14, 25, 2000);
    private final Category strict = category("strict", 2, 2, 16, 16, 1200);
    private final Category impossible = category("impossible", 1, 4, 50, 60, 2000);
    private final Edition first = edition("ed-1", DATE, broad, impossible);
    private final Edition second = edition("ed-2", DATE.plusDays(1), strict);
    private final Team canonical = team("team-1");

    UpdateTeamUseCaseTest() {
        editions.save(first);
        editions.save(second);
        register.execute(first.getId(), broad.id(), canonical);
        register.execute(second.getId(), strict.id(), canonical.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"last-member", "one-member", "robot", "documentation", "document-change", "age"})
    void invalidUpdatePreservesCanonicalStateAndAllRegistrations(String change) {
        Team candidate = switch (change) {
            case "last-member" -> canonical.withMembers(List.of());
            case "one-member" -> canonical.withMembers(List.of(canonical.getMembers().getFirst()));
            case "robot" -> canonical.withRobot(robot(1500)); // Eligible in ed-1, too heavy in ed-2.
            case "documentation" -> canonical.withDocumentation(canonical.getDocumentation().revokeVerification("Missing file"));
            case "document-change" -> canonical.withDocumentation(canonical.getDocumentation().withDocument("CONSENT", "changed.pdf"));
            case "age" -> canonical.withMembers(List.of(member("old-1", DATE.minusYears(17)), member("old-2", DATE.minusYears(17))));
            default -> throw new AssertionError(change);
        };
        var before = editions.findByTeamId(canonical.getId());
        assertThatThrownBy(() -> update.execute(canonical.getId(), candidate))
                .isInstanceOfSatisfying(TeamIneligibleException.class,
                        e -> assertThat(e.getViolations()).anyMatch(v -> v.startsWith("Edition ed-2:")));
        assertThat(teams.findById(canonical.getId())).containsSame(canonical);
        for (Edition edition : before) {
            assertThat(editions.findById(edition.getId())).containsSame(edition);
        }
        assertThat(canonical.getMembers()).hasSize(2);
        assertThat(canonical.getRobot().getSpecification().weight().grams()).isEqualTo(1000);
        assertThat(canonical.getDocumentation().isVerified()).isTrue();
    }

    @Test
    void collectsViolationsFromAllEditionsInsteadOfStoppingAtFirst() {
        assertThatThrownBy(() -> update.execute(canonical.getId(), canonical.withMembers(List.of())))
                .isInstanceOfSatisfying(TeamIneligibleException.class, e ->
                        assertThat(e.getViolations()).hasSize(2).anyMatch(v -> v.startsWith("Edition ed-1:"))
                                .anyMatch(v -> v.startsWith("Edition ed-2:")));
    }

    @Test
    void validUpdateIsCanonicalForBothEditionsAndTheirRankings() {
        var before = editions.findByTeamId(canonical.getId());
        Team changed = canonical.withRobot(robot(1100))
                .withProfile(TeamProfile.of(canonical.getId(), "New name", "New school"))
                .withDocumentation(canonical.getDocumentation().withDocument("CONSENT", "new.pdf")
                        .verify(ActorId.of("inspector-2"), TIME.plusHours(1)));
        assertThat(update.execute(canonical.getId(), changed)).isSameAs(changed);
        assertThat(teams.findById(canonical.getId())).containsSame(changed);
        InMemoryChallengeRepository challenges = new InMemoryChallengeRepository();
        RecalculateStandingsUseCase standings = new RecalculateStandingsUseCase(new CategoryResultsReader(challenges,
                editions, teams, new InMemoryRoundRepository(), new InMemoryAttemptRepository()),
                new InMemoryStandingsRepository(), CLOCK);
        for (Edition edition : before) {
            assertThat(editions.findById(edition.getId())).containsSame(edition);
            Registration registration = edition.registration(canonical.getId()).orElseThrow();
            Challenge challenge = Challenge.draft(ChallengeId.of("ch-" + edition.getId()), edition.getId(), "Laberinto")
                    .publish(ScoringScheme.withoutBonuses(List.of(new ObjectivesRule("Objetivos", 20.0)), List.of()),
                            new RankingScheme(new AllRounds(), List.of(new HigherTotal())));
            challenges.save(challenge);
            assertThat(standings.execute(new StandingsId(challenge.getId(), registration.categoryId()))
                    .latest().table().entries())
                    .singleElement().satisfies(entry -> assertThat(entry.team().teamName()).isEqualTo("New name"));
        }
        assertThat(canonical.getName()).isEqualTo("team-1");
        assertThat(canonical.getRobot().getSpecification().weight().grams()).isEqualTo(1000);
    }

    @Test
    void categoryChangeIsScopedToOneEditionAndPreservesEnrollmentTime() {
        Category alternative = category("alternative", 1, 3, 15, 20, 1800);
        Edition offered = editions.findById(first.getId()).orElseThrow().addCategory(alternative);
        editions.save(offered);
        Registration old = offered.registration(canonical.getId()).orElseThrow();
        Edition other = editions.findById(second.getId()).orElseThrow();
        Registration changed = changeCategory.execute(first.getId(), canonical.getId(), alternative.id());
        assertThat(changed.categoryId()).isEqualTo(alternative.id());
        assertThat(changed.registeredAt()).isEqualTo(old.registeredAt());
        assertThat(changed.referenceDate()).isEqualTo(old.referenceDate());
        assertThat(offered.registration(canonical.getId())).contains(old);
        assertThat(editions.findById(second.getId())).containsSame(other);
        assertThat(other.registration(canonical.getId()).orElseThrow().categoryId()).isEqualTo(strict.id());
        assertThat(teams.findById(canonical.getId())).containsSame(canonical);
    }

    @Test
    void foreignOrIneligibleCategoryChangePreservesTheEdition() {
        Edition before = editions.findById(first.getId()).orElseThrow();
        assertThatThrownBy(() -> changeCategory.execute(first.getId(), canonical.getId(), CategoryId.of("foreign")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not offered");
        assertThatThrownBy(() -> changeCategory.execute(first.getId(), canonical.getId(), impossible.id()))
                .isInstanceOf(TeamIneligibleException.class);
        assertThat(editions.findById(first.getId())).containsSame(before);
        assertThat(teams.findById(canonical.getId())).containsSame(canonical);
    }

    @Test
    void updateCannotCreateTeamsOrChangeTheirIdentity() {
        assertThatThrownBy(() -> update.execute(TeamId.of("missing"), canonical))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Team not found");
        assertThatThrownBy(() -> update.execute(canonical.getId(), team("different")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot change its identity");
        assertThatThrownBy(() -> canonical.withProfile(TeamProfile.of(TeamId.of("different"), "Name", "School")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(teams.findAll()).containsExactly(canonical);
    }
}
