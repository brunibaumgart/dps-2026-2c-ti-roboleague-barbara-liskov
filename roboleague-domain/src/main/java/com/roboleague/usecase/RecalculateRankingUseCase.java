package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.RankingCalculatorService;
import com.roboleague.ranking.tiebreakers.TieBreakerChain;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RankingRepository;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.Team;

import java.util.*;

/**
 * Use case to recalculate the leaderboard for a category and edition,
 * leveraging the latest audited attempt snapshots and deterministic tie-breaking.
 */
public class RecalculateRankingUseCase {
    private final EditionRepository editionRepository;
    private final AttemptRepository attemptRepository;
    private final RankingRepository rankingRepository;
    private final RankingCalculatorService rankingCalculatorService;

    public RecalculateRankingUseCase(EditionRepository editionRepository,
                                    AttemptRepository attemptRepository,
                                    RankingRepository rankingRepository,
                                    RankingCalculatorService rankingCalculatorService) {
        this.editionRepository = Objects.requireNonNull(editionRepository, "editionRepository cannot be null");
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.rankingRepository = Objects.requireNonNull(rankingRepository, "rankingRepository cannot be null");
        this.rankingCalculatorService = rankingCalculatorService != null ? rankingCalculatorService : new RankingCalculatorService();
    }

    public Ranking execute(String editionId, String categoryId, String roundId) {
        Edition edition = editionRepository.findById(editionId)
                .orElseThrow(() -> new IllegalArgumentException("Edition not found: " + editionId));

        List<Team> teams = edition.getTeamsByCategory(categoryId);
        Map<String, List<Attempt>> attemptsByTeam = new HashMap<>();

        for (Team team : teams) {
            List<Attempt> teamAttempts = attemptRepository.findByTeamId(team.getId());
            // Filter attempts matching the round if roundId is specified
            if (roundId != null && !roundId.isEmpty()) {
                teamAttempts = teamAttempts.stream()
                        .filter(a -> a.getRoundId().equals(roundId))
                        .toList();
            }
            attemptsByTeam.put(team.getId(), teamAttempts);
        }

        String rankingId = UUID.randomUUID().toString();
        Ranking ranking = rankingCalculatorService.calculateProvisionalRanking(
                rankingId, editionId, categoryId, roundId, teams, attemptsByTeam
        );

        rankingRepository.save(ranking);
        return ranking;
    }
}
