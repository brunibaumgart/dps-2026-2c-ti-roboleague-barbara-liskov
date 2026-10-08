package com.roboleague.repository;

import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.RankingId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;

import java.util.List;
import java.util.Optional;

public interface RankingRepository {
    void save(Ranking ranking);
    Optional<Ranking> findById(RankingId rankingId);
    Optional<Ranking> findLatestByEditionAndCategory(EditionId editionId, CategoryId categoryId);
    List<Ranking> findAll();
}
