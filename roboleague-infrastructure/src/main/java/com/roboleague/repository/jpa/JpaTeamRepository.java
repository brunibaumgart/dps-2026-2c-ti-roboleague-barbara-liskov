package com.roboleague.repository.jpa;

import com.roboleague.repository.TeamRepository;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Driven adapter: implements the domain port {@link TeamRepository} on Postgres. Teams are immutable; concurrent
 * updates are detected with the version read in the same transaction, as in {@link JpaEditionRepository}.
 */
@Repository
public class JpaTeamRepository implements TeamRepository {

    private final SpringDataTeams jpa;

    JpaTeamRepository(SpringDataTeams jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Team team) {
        Objects.requireNonNull(team, "team cannot be null");
        TeamJpaEntity entity = TeamMapper.toEntity(team);
        entity.version = jpa.findById(entity.id).map(read -> read.version).orElse(null);
        WriteConflicts.write(() -> jpa.saveAndFlush(entity), "Team " + team.getId()
                + " was changed by someone else while it was being updated; load it again and retry");
    }

    @Override
    public Optional<Team> findById(TeamId id) {
        return jpa.findById(id.value()).map(TeamMapper::toDomain);
    }

    @Override
    public List<Team> findAll() {
        return jpa.findAllByOrderByIdAsc().stream().map(TeamMapper::toDomain).toList();
    }
}
