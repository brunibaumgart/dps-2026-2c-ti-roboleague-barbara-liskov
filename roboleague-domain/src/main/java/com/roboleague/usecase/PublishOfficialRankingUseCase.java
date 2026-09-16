package com.roboleague.usecase;

import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.RankingRepository;

import java.util.List;
import java.util.Objects;

/**
 * Use case to validate and publish an Official Ranking.
 * Ensures that no unresolved (Pending or Under Review) appeals exist before locking in official results.
 */
public class PublishOfficialRankingUseCase {
    private final RankingRepository rankingRepository;
    private final AppealRepository appealRepository;

    public PublishOfficialRankingUseCase(RankingRepository rankingRepository, AppealRepository appealRepository) {
        this.rankingRepository = Objects.requireNonNull(rankingRepository, "rankingRepository cannot be null");
        this.appealRepository = Objects.requireNonNull(appealRepository, "appealRepository cannot be null");
    }

    public Ranking execute(String rankingId, String officialNotes) {
        Ranking ranking = rankingRepository.findById(rankingId)
                .orElseThrow(() -> new IllegalArgumentException("Ranking not found: " + rankingId));

        List<Appeal> blockingAppeals = appealRepository.findAll().stream()
                .filter(a -> !a.canPublishOfficialRanking())
                .toList();

        if (!blockingAppeals.isEmpty()) {
            throw new IllegalStateException("Cannot publish official ranking while " + blockingAppeals.size() + " appeal(s) remain unresolved");
        }

        ranking.publishOfficial(officialNotes);
        rankingRepository.save(ranking);
        return ranking;
    }
}
