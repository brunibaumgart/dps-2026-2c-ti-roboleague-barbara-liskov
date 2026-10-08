package com.roboleague.repository;

import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;

import java.util.List;
import java.util.Optional;

public interface TeamRepository {
    void save(Team team);
    Optional<Team> findById(TeamId id);
    List<Team> findAll();
    List<Team> findByCategory(CategoryId categoryId);
}
