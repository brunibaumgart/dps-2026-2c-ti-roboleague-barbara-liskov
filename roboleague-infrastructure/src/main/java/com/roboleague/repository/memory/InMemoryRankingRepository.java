package com.roboleague.repository.memory;

import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.RankingId;
import com.roboleague.repository.RankingRepository;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRankingRepository implements RankingRepository {
    private final Map<RankingId, Ranking> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Ranking ranking) {
        Objects.requireNonNull(ranking, "ranking cannot be null");
        storage.put(ranking.getRankingId(), ranking);
    }

    @Override
    public Optional<Ranking> findById(RankingId rankingId) {
        return Optional.ofNullable(storage.get(rankingId));
    }

    @Override
    public Optional<Ranking> findLatestByEditionAndCategory(EditionId editionId, CategoryId categoryId) {
        return storage.values().stream()
                .filter(r -> r.getEditionId().equals(editionId) && r.getCategoryId().equals(categoryId))
                .max(Comparator.comparing(Ranking::getGeneratedAt));
    }

    @Override
    public List<Ranking> findAll() {
        return new ArrayList<>(storage.values());
    }
}
