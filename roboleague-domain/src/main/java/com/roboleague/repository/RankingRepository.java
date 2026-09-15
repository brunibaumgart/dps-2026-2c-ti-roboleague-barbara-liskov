package com.roboleague.repository;

import com.roboleague.ranking.Ranking;

import java.util.List;
import java.util.Optional;

public interface RankingRepository {
    void save(Ranking ranking);
    Optional<Ranking> findById(String rankingId);
    Optional<Ranking> findLatestByEditionAndCategory(String editionId, String categoryId);
    List<Ranking> findAll();
}
