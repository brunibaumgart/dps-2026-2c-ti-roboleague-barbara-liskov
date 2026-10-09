package com.roboleague.repository;

import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;

import java.util.List;
import java.util.Optional;

public interface EditionRepository {
    void save(Edition edition);
    Optional<Edition> findById(EditionId id);
    List<Edition> findAll();
    List<Edition> findByTeamId(TeamId teamId);
}
