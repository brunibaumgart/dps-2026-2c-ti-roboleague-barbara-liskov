package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.RankingEntry;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.RankingRepository;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Use case to validate and publish an Official Ranking.
 * Ensures that no unresolved (Pending or Under Review) appeals exist over the attempts
 * that feed this ranking before locking in official results. Appeals filed in other
 * rounds or categories do not block the publication.
 */
public class PublishOfficialRankingUseCase {
    private final RankingRepository rankingRepository;
    private final AppealRepository appealRepository;
    private final AttemptRepository attemptRepository;

    public PublishOfficialRankingUseCase(RankingRepository rankingRepository,
                                         AppealRepository appealRepository,
                                         AttemptRepository attemptRepository) {
        this.rankingRepository = Objects.requireNonNull(rankingRepository, "rankingRepository cannot be null");
        this.appealRepository = Objects.requireNonNull(appealRepository, "appealRepository cannot be null");
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
    }

    public Ranking execute(String rankingId, String officialNotes) {
        Ranking ranking = rankingRepository.findById(rankingId)
                .orElseThrow(() -> new IllegalArgumentException("Ranking not found: " + rankingId));

        if (ranking.isOfficial()) {
            throw new IllegalStateException("Ranking " + rankingId + " is already official");
        }

        List<Appeal> blockingAppeals = appealRepository.findAll().stream()
                .filter(a -> !a.canPublishOfficialRanking())
                .filter(a -> affectsRanking(a, ranking))
                .toList();

        if (!blockingAppeals.isEmpty()) {
            throw new IllegalStateException("Cannot publish official ranking while " + blockingAppeals.size() + " appeal(s) remain unresolved");
        }

        ranking.publishOfficial(officialNotes);
        rankingRepository.save(ranking);
        return ranking;
    }

    private boolean affectsRanking(Appeal appeal, Ranking ranking) {
        Set<String> rankedTeamIds = ranking.getEntries().stream()
                .map(RankingEntry::teamScore)
                .map(score -> score.teamId())
                .collect(Collectors.toSet());

        return attemptRepository.findById(appeal.getAttemptId())
                .filter(attempt -> rankedTeamIds.contains(attempt.getTeamId()))
                .filter(attempt -> belongsToRound(attempt, ranking))
                .isPresent();
    }

    private boolean belongsToRound(Attempt attempt, Ranking ranking) {
        return ranking.getRoundId().isEmpty() || ranking.getRoundId().equals(attempt.getRoundId());
    }
}
