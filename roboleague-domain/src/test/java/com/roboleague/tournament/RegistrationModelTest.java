package com.roboleague.tournament;

import com.roboleague.support.ActorId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.roboleague.support.TestValues.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationModelTest {
    private final Category category = Category.of(CategoryId.of("junior"), "Junior", 1, 2, 10, 20, 2000);
    private final TeamMember member = TeamMember.of(ParticipantId.of("m-1"), "Member", DATE.minusYears(16), "MEMBER", DATE);
    private final Robot robot = new Robot(RobotId.of("robot-1"), "Robot", RobotSpecification.of(1000, 100, 100, 100, 1, Set.of("LIDAR")));
    private final Documentation documentation = new Documentation().withDocument("CONSENT", "consent.pdf")
            .verify(ActorId.of("inspector"), TIME);
    private final Team team = Team.of(TeamId.of("team-1"), "Team", "School", robot, List.of(member), documentation);
    private final Edition edition = Edition.of(EditionId.of("ed-1"),
            Tournament.of("t-1", "Tournament", new Season("s-1", 2026, "2026")), 1, "Edition", DATE, DATE.plusDays(1), List.of(category));

    @Test
    void returnedCandidatesDoNotMutateTheOriginalTeamOrNestedValues() {
        Team candidate = team.withMembers(List.of()).withRobot(robot.withSpecification(
                RobotSpecification.of(3000, 100, 100, 100, 1, Set.of())))
                .withDocumentation(documentation.revokeVerification("Missing consent"));
        assertThat(team.getMembers()).containsExactly(member);
        assertThat(team.getRobot().getSpecification().weight().grams()).isEqualTo(1000);
        assertThat(team.getDocumentation().isVerified()).isTrue();
        assertThat(candidate.getMembers()).isEmpty();
        assertThat(candidate.getDocumentation().isVerified()).isFalse();
        assertThat(documentation.withDocument("CONSENT", "replacement.pdf").isVerified()).isFalse();
    }

    @Test
    void constructorsAndGettersProtectEveryNestedCollection() {
        var members = new ArrayList<>(List.of(member));
        var sensors = new HashSet<>(Set.of("LIDAR"));
        var documents = new HashMap<String, String>();
        documents.put("CONSENT", "original.pdf");
        RobotHardware hardware = new RobotHardware(1, sensors);
        Documentation restored = Documentation.restore(documents, true, TIME, ActorId.of("inspector"), null);
        Team snapshot = new Team(team.getProfile(), robot, members, restored);
        Edition enrolled = edition.registerTeam(snapshot, category.id(), TIME);
        members.clear();
        sensors.clear();
        documents.clear();
        assertThat(snapshot.getMembers()).containsExactly(member);
        assertThat(hardware.sensors()).containsExactly("LIDAR");
        assertThat(restored.getDocuments()).containsEntry("CONSENT", "original.pdf");
        assertThatThrownBy(() -> snapshot.getMembers().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.getDocuments().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> hardware.sensors().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> enrolled.getRegistrations().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(edition.getRegistrations()).isEmpty();
    }

    @Test
    void registrationRestoresRelationshipsAndTimesWithoutReplayingEligibility() {
        Registration historical = new Registration(team.getId(), edition.getId(), category.id(), DATE, TIME.minusYears(2));
        var relations = new ArrayList<>(List.of(historical));
        Edition restored = Edition.restore(edition.getContext(), edition.getDates(), edition.getCategories(), relations);
        relations.clear();
        assertThat(restored.getRegistrations()).containsExactly(historical);
        assertThat(restored.registration(team.getId())).contains(historical);
        assertThat(restored.getRegistrationsByCategory(category.id())).containsExactly(historical);
        assertThat(restored.registration(team.getId()).orElseThrow().registeredAt()).isEqualTo(TIME.minusYears(2));
        assertThatThrownBy(() -> Edition.restore(edition.getContext(), edition.getDates(), edition.getCategories(),
                List.of(historical, historical))).isInstanceOf(IllegalStateException.class);
        Registration foreign = new Registration(team.getId(), EditionId.of("another-edition"), category.id(), DATE, TIME);
        assertThatThrownBy(() -> Edition.restore(edition.getContext(), edition.getDates(), edition.getCategories(), List.of(foreign)))
                .isInstanceOf(IllegalArgumentException.class);
        Registration wrongDate = new Registration(team.getId(), edition.getId(), category.id(), DATE.plusDays(1), TIME);
        assertThatThrownBy(() -> Edition.restore(edition.getContext(), edition.getDates(), edition.getCategories(), List.of(wrongDate)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicEnrollmentOperationsValidateAndRejectDuplicatesWithoutMutation() {
        Edition enrolled = edition.registerTeam(team, category.id(), TIME);
        assertThatThrownBy(() -> enrolled.registerTeam(team, category.id(), TIME.plusDays(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> edition.registerTeam(team, CategoryId.of("foreign"), TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(edition.getRegistrations()).isEmpty();
        assertThat(enrolled.getRegistrations()).hasSize(1);
    }

    @Test
    void registrationIdentityIsTheEditionAndTeamRegardlessOfCategoryOrTime() {
        Registration first = new Registration(team.getId(), edition.getId(), category.id(), DATE, TIME);
        Registration changed = new Registration(team.getId(), edition.getId(), CategoryId.of("other"), DATE, TIME.plusDays(1));
        Registration anotherEdition = new Registration(team.getId(), EditionId.of("ed-2"), category.id(), DATE, TIME);
        assertThat(changed).isEqualTo(first).hasSameHashCodeAs(first).isNotEqualTo(anotherEdition);
    }

    @Test
    void participantIdentityCannotAppearTwiceEvenWithDifferentProfileData() {
        TeamMember sameId = TeamMember.of(member.id(), "Different name", member.birthDate(), "LEADER", DATE);
        assertThatThrownBy(() -> team.withMembers(List.of(member, sameId)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Member already registered");
    }
}
