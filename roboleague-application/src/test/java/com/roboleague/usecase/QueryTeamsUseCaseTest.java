package com.roboleague.usecase;

import com.roboleague.port.in.InconsistentStateException;
import com.roboleague.port.in.NotFoundException;

import com.roboleague.repository.memory.InMemoryTeamRepository;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.roboleague.usecase.RegistrationFixtures.team;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryTeamsUseCaseTest {
    private final InMemoryTeamRepository teams = new InMemoryTeamRepository();
    private final ReadCounts reads = new ReadCounts();
    private final QueryTeamsUseCase query = new QueryTeamsUseCase(new CountingTeamRepository(teams, reads));

    QueryTeamsUseCaseTest() {
        teams.save(team("team-a"));
        teams.save(team("team-b"));
    }

    @Test
    void givenAStoredTeamThenItIsReturned() {
        assertThat(query.get(TeamId.of("team-a")).getId()).isEqualTo(TeamId.of("team-a"));
    }

    @Test
    void givenAnUnknownTeamThenItIsNotFound() {
        assertThatThrownBy(() -> query.get(TeamId.of("none")))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.resource()).isEqualTo("Team"))
                .hasMessage("Team not found: none");
    }

    @Test
    void givenRepeatedIdsThenEachTeamComesOnceKeyedById() {
        Map<TeamId, Team> found = query.byIds(List.of(TeamId.of("team-b"), TeamId.of("team-a"), TeamId.of("team-b")));

        assertThat(found).containsOnlyKeys(TeamId.of("team-a"), TeamId.of("team-b"));
        assertThat(found.get(TeamId.of("team-a")).getId()).isEqualTo(TeamId.of("team-a"));
        assertThat(reads.of("findByIds")).isEqualTo(1);
        assertThat(reads.total()).isEqualTo(1);
    }

    @Test
    void givenAMissingIdThenTheStoredReferenceIsInconsistent() {
        assertThatThrownBy(() -> query.byIds(List.of(TeamId.of("team-a"), TeamId.of("ghost"))))
                .isInstanceOf(InconsistentStateException.class)
                .hasMessageContaining("Registered team not found").hasMessageContaining("ghost");
    }

    @Test
    void givenNoIdsThenTheMapIsEmptyWithoutReading() {
        assertThat(query.byIds(List.of())).isEmpty();
        assertThat(reads.total()).isZero();
    }
}
