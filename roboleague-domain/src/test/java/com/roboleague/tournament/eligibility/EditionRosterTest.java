package com.roboleague.tournament.eligibility;

import com.roboleague.tournament.Documentation;
import com.roboleague.tournament.ParticipantId;
import com.roboleague.tournament.Robot;
import com.roboleague.tournament.RobotId;
import com.roboleague.tournament.RobotSpecification;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import com.roboleague.tournament.TeamMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.roboleague.support.TestValues.DATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EditionRosterTest {

    private static Team team(String id, String robot, String... members) {
        List<TeamMember> people = Arrays.stream(members)
                .map(member -> TeamMember.of(ParticipantId.of(member), member, LocalDate.of(2010, 1, 1), "MEMBER", DATE))
                .toList();
        return Team.of(TeamId.of(id), "Team " + id, "School",
                new Robot(RobotId.of(robot), "Bot", RobotSpecification.of(1000, 100, 100, 100, 2, Set.of("LIDAR"))),
                people, new Documentation());
    }

    private static final EditionRoster ROSTER = new EditionRoster(List.of(
            team("t-a", "r-a", "m-1", "m-2"), team("t-b", "r-b", "m-3")));

    @Test
    @DisplayName("Un equipo sin integrantes ni robot de otro equipo de la edición no tiene conflictos")
    void aTeamWithItsOwnPeopleAndRobotHasNoConflicts() {
        assertThat(ROSTER.conflictsWith(team("t-c", "r-c", "m-4", "m-5"))).isEmpty();
        assertThatCode(() -> ROSTER.requireNoConflicts(team("t-c", "r-c", "m-4"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La propia inscripción del candidato no es un conflicto, aunque cambie su equipo")
    void theCandidatesOwnRegistrationIsNotAConflict() {
        assertThat(ROSTER.conflictsWith(team("t-a", "r-a", "m-1", "m-9"))).isEmpty();
    }

    @Test
    @DisplayName("Cada integrante y el robot compartidos con otro equipo son un conflicto con nombre")
    void everySharedParticipantAndRobotIsNamed() {
        assertThat(ROSTER.conflictsWith(team("t-c", "r-b", "m-1", "m-3", "m-4"))).containsExactly(
                "Participant m-1 already competes for team t-a",
                "Participant m-3 already competes for team t-b",
                "Robot r-b already competes for team t-b");
    }

    @Test
    @DisplayName("Un candidato con conflictos se rechaza como inelegible con cada motivo")
    void aConflictingCandidateIsIneligibleWithEveryReason() {
        assertThatThrownBy(() -> ROSTER.requireNoConflicts(team("t-c", "r-a", "m-2")))
                .isInstanceOfSatisfying(TeamIneligibleException.class, rejected -> {
                    assertThat(rejected).hasMessage("Team Team t-c shares participants or robot with another team "
                            + "of the edition: Participant m-2 already competes for team t-a; "
                            + "Robot r-a already competes for team t-a");
                    assertThat(rejected.getViolations()).containsExactly(
                            "Participant m-2 already competes for team t-a", "Robot r-a already competes for team t-a");
                });
    }
}
