package com.roboleague.port.in;

import com.roboleague.support.ActorId;
import com.roboleague.tournament.ParticipantId;
import com.roboleague.tournament.Robot;
import com.roboleague.tournament.RobotId;
import com.roboleague.tournament.RobotSpecification;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamCandidateTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0);
    private static final Robot ROBOT = new Robot(RobotId.of("robot-1"), "Robot",
            RobotSpecification.of(1000, 100, 100, 100, 2, Set.of("LIDAR")));
    private static final TeamCandidate.Member ANA =
            new TeamCandidate.Member(ParticipantId.of("m-1"), "Ana", LocalDate.of(2010, 1, 1), "CAPTAIN");

    @Test
    void theTeamTakesItsDatesFromTheGivenMomentNotFromTheCandidate() {
        Team team = candidate(Optional.of(ActorId.of("inspector"))).toTeam(NOW);

        assertThat(team.getId()).isEqualTo(TeamId.of("t-1"));
        assertThat(team.getName()).isEqualTo("Team");
        assertThat(team.getInstitution()).isEqualTo("School");
        assertThat(team.getRobot()).isEqualTo(ROBOT);
        assertThat(team.getMembers()).singleElement().satisfies(member -> {
            assertThat(member.id()).isEqualTo(ParticipantId.of("m-1"));
            assertThat(member.fullName()).isEqualTo("Ana");
            assertThat(member.birthDate()).isEqualTo(LocalDate.of(2010, 1, 1));
            assertThat(member.role()).isEqualTo("CAPTAIN");
        });
        assertThat(team.getDocumentation().isVerified()).isTrue();
        assertThat(team.getDocumentation().getVerifiedAt()).isEqualTo(NOW);
        assertThat(team.getDocumentation().getVerifiedBy()).isEqualTo(ActorId.of("inspector"));
    }

    @Test
    void withoutAVerifierTheDocumentationIsNotVerifiedButKeepsEveryDocument() {
        Team team = candidate(Optional.empty()).toTeam(NOW);

        assertThat(team.getDocumentation().isVerified()).isFalse();
        assertThat(team.getDocumentation().getVerifiedAt()).isNull();
        assertThat(team.getDocumentation().getDocuments())
                .containsOnly(Map.entry("SAFETY", "safety.pdf"), Map.entry("CONSENT", "consent.pdf"));
    }

    @Test
    void theCandidateCannotBeChangedAfterItIsBuilt() {
        Map<String, String> documents = new LinkedHashMap<>(Map.of("CONSENT", "consent.pdf"));
        TeamCandidate candidate = new TeamCandidate(TeamId.of("t-1"), "Team", "School", ROBOT,
                new java.util.ArrayList<>(List.of(ANA)), documents, Optional.empty());
        documents.put("EXTRA", "extra.pdf");

        assertThat(candidate.documents()).containsOnlyKeys("CONSENT");
        assertThatThrownBy(() -> candidate.members().add(ANA)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void everyPartIsRequired() {
        assertThatThrownBy(() -> new TeamCandidate(null, "Team", "School", ROBOT, List.of(ANA), Map.of(),
                Optional.empty())).isInstanceOf(NullPointerException.class).hasMessageContaining("id");
        assertThatThrownBy(() -> new TeamCandidate(TeamId.of("t-1"), "Team", "School", ROBOT, List.of(ANA), Map.of(),
                null)).isInstanceOf(NullPointerException.class).hasMessageContaining("verifiedBy");
        assertThatThrownBy(() -> new TeamCandidate.Member(ParticipantId.of("m-1"), "Ana", null, "CAPTAIN"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("birthDate");
        assertThatThrownBy(() -> candidate(Optional.empty()).toTeam(null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("now");
    }

    private static TeamCandidate candidate(Optional<ActorId> verifier) {
        Map<String, String> documents = new LinkedHashMap<>();
        documents.put("SAFETY", "safety.pdf");
        documents.put("CONSENT", "consent.pdf");
        return new TeamCandidate(TeamId.of("t-1"), "Team", "School", ROBOT, List.of(ANA), documents, verifier);
    }
}
