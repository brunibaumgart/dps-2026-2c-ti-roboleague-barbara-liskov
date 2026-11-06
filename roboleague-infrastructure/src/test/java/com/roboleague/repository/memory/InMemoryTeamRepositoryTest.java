package com.roboleague.repository.memory;

import com.roboleague.tournament.Documentation;
import com.roboleague.tournament.MemberProfile;
import com.roboleague.tournament.ParticipantId;
import com.roboleague.tournament.Robot;
import com.roboleague.tournament.RobotId;
import com.roboleague.tournament.RobotSpecification;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import com.roboleague.tournament.TeamMember;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTeamRepositoryTest {
    @Test
    void findsByIdsOnceOrderedByIdSkippingMissingOnesAndEmptyGivesNothing() {
        var repository = new InMemoryTeamRepository();
        for (String id : List.of("b", "a", "c")) {
            repository.save(team(id));
        }
        assertThat(repository.findByIds(List.of(TeamId.of("c"), TeamId.of("a"), TeamId.of("none"), TeamId.of("a"))))
                .extracting(team -> team.getId().value()).containsExactly("a", "c");
        assertThat(repository.findByIds(List.of())).isEmpty();
    }

    private static Team team(String id) {
        return Team.of(TeamId.of(id), "Team " + id, "School",
                new Robot(RobotId.of("rb-" + id), "Bolt", RobotSpecification.of(1000.0, 200.0, 180.0, 150.0, 4,
                        java.util.Set.of("lidar"))),
                List.of(TeamMember.of(ParticipantId.of(id + "-m"), "Ana", java.time.LocalDate.of(2010, 5, 20),
                        "CAPTAIN", java.time.LocalDate.of(2026, 11, 10))),
                new Documentation());
    }
}
