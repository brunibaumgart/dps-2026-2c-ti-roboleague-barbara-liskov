package com.roboleague.repository;

import com.roboleague.tournament.Team;

import java.util.List;
import java.util.Optional;

public interface TeamRepository {
    void save(Team team);
    Optional<Team> findById(String id);
    List<Team> findAll();
    List<Team> findByCategory(String categoryId);
}
