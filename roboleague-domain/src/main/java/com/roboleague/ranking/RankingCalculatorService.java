package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.ranking.tiebreakers.TieBreakerChain;
import com.roboleague.scheduling.RoundId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain service calculating leaderboards and ranking positions for a category in an edition.
 */
public class RankingCalculatorService {

    private final TieBreakerChain tieBreakerChain;

    public RankingCalculatorService(TieBreakerChain tieBreakerChain) {
        this.tieBreakerChain = Objects.requireNonNull(tieBreakerChain, "tieBreakerChain cannot be null");
    }

    public RankingCalculatorService() {
        this(TieBreakerChain.defaultRules());
    }

    public Ranking calculateProvisionalRanking(RankingScope scope, Optional<RoundId> roundId, RankingCalculationData data, LocalDateTime generatedAt) {
        Objects.requireNonNull(scope, "scope cannot be null");
        Objects.requireNonNull(data, "data cannot be null");

        List<TeamScore> teamScores = new ArrayList<>();
        for (Team team : data.teams()) {
            List<Attempt> teamAttempts = data.attemptsByTeamId().getOrDefault(team.getId(), List.of());
            TeamScore score = TeamScore.fromBestAttempt(
                    team.getId(),
                    team.getName(),
                    scope.categoryId(),
                    scope.editionId(),
                    teamAttempts
            );
            teamScores.add(score);
        }

        teamScores.sort(tieBreakerChain);

        List<RankingEntry> entries = new ArrayList<>();
        int currentRank = 1;

        for (int i = 0; i < teamScores.size(); i++) {
            TeamScore current = teamScores.get(i);
            boolean tiedWithPrev = false;
            String explanation = "Posición asignada por criterios estándar";

            if (i > 0) {
                TeamScore prev = teamScores.get(i - 1);
                if (current.isTiedWith(prev)) {
                    tiedWithPrev = true;
                    explanation = "Empate técnico con posición previa en puntaje, tiempo, faltas y jueces";
                } else {
                    currentRank = i + 1;
                }
            }

            entries.add(RankingEntry.of(currentRank, current, tiedWithPrev, explanation));
        }

        return new Ranking(scope, roundId, entries, generatedAt);
    }

    public Ranking calculateProvisionalRanking(RankingId rankingId, EditionId editionId, CategoryId categoryId, Optional<RoundId> roundId,
                                              List<Team> teams, Map<TeamId, List<Attempt>> attemptsByTeamId, LocalDateTime generatedAt) {
        RankingScope scope = RankingScope.of(rankingId, editionId, categoryId);
        RankingCalculationData data = RankingCalculationData.of(teams, attemptsByTeamId);
        return calculateProvisionalRanking(scope, roundId, data, generatedAt);
    }
}
