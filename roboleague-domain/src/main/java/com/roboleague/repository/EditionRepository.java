package com.roboleague.repository;

import com.roboleague.tournament.Edition;

import java.util.List;
import java.util.Optional;

public interface EditionRepository {
    void save(Edition edition);
    Optional<Edition> findById(String id);
    List<Edition> findAll();
}
