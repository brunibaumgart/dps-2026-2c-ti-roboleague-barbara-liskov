package com.roboleague.repository;

import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EditionRepository {
    void save(Edition edition);
    Optional<Edition> findById(EditionId id);
    /** The editions with those ids, ordered by id; ids that do not exist are simply absent. */
    List<Edition> findByIds(Collection<EditionId> ids);
    List<Edition> findAll();
    List<Edition> findByTeamId(TeamId teamId);
}
