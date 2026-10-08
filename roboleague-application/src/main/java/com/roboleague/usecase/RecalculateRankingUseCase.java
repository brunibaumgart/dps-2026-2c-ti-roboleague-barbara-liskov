package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.ranking.Ranking;
import com.roboleague.ranking.RankingCalculatorService;
import com.roboleague.ranking.RankingId;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RankingRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.scheduling.RoundId;
import com.roboleague.support.Clock;
import com.roboleague.support.IdGenerator;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;

import java.util.*;

/**
 * Use case to recalculate the leaderboard for a category and edition,
 * leveraging the latest audited attempt snapshots and deterministic tie-breaking.
 */
public class RecalculateRankingUseCase {
    private final Clock clock;
    private final IdGenerator ids;

    private final EditionRepository editionRepository;
    private final TeamRepository teamRepository;
    private final AttemptRepository attemptRepository;
    private final RankingRepository rankingRepository;
    private final RankingCalculatorService rankingCalculatorService;

    public RecalculateRankingUseCase(EditionRepository editionRepository, TeamRepository teamRepository,
                                    AttemptRepository attemptRepository,
                                    RankingRepository rankingRepository,
                                    RankingCalculatorService rankingCalculatorService, Clock clock, IdGenerator ids) {
        this.teamRepository = Objects.requireNonNull(teamRepository, "teamRepository cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.ids = Objects.requireNonNull(ids, "ids cannot be null");
        this.editionRepository = Objects.requireNonNull(editionRepository, "editionRepository cannot be null");
        this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository cannot be null");
        this.rankingRepository = Objects.requireNonNull(rankingRepository, "rankingRepository cannot be null");
        this.rankingCalculatorService = Objects.requireNonNull(rankingCalculatorService, "rankingCalculatorService cannot be null");
    }

    public Ranking execute(EditionId editionId, CategoryId categoryId, Optional<RoundId> roundId) {
        Edition edition = editionRepository.findById(editionId)
                .orElseThrow(() -> new IllegalArgumentException("Edition not found: " + editionId));

        List<Team> teams = edition.getRegistrationsByCategory(categoryId).stream()
                .map(registration -> teamRepository.findById(registration.teamId())
                        .orElseThrow(() -> new IllegalStateException("Registered team not found: " + registration.teamId())))
                .toList();
        Map<TeamId, List<Attempt>> attemptsByTeam = new HashMap<>();

        for (Team team : teams) {
            List<Attempt> teamAttempts = attemptRepository.findByTeamId(team.getId());
            if (roundId.isPresent()) {
                teamAttempts = teamAttempts.stream()
                        .filter(a -> a.getRoundId().equals(roundId.orElseThrow()))
                        .toList();
            }
            attemptsByTeam.put(team.getId(), teamAttempts);
        }

        RankingId rankingId = RankingId.of(ids.nextId());
        Ranking ranking = rankingCalculatorService.calculateProvisionalRanking(
                rankingId, editionId, categoryId, roundId, teams, attemptsByTeam, clock.now()
        );

        rankingRepository.save(ranking);
        return ranking;
    }
}
