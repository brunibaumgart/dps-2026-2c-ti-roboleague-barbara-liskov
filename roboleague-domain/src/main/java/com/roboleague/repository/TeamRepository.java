package com.roboleague.repository;

import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamRepository {
    void save(Team team);
    Optional<Team> findById(TeamId id);
    /** The teams with those ids, ordered by id; ids that do not exist are simply absent. */
    List<Team> findByIds(Collection<TeamId> ids);
    List<Team> findAll();
}
