package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.ranking.tiebreakers.TieBreakerChain;
import com.roboleague.tournament.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public Ranking calculateProvisionalRanking(RankingScope scope, String roundId, RankingCalculationData data) {
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

        return new Ranking(scope, roundId, entries);
    }

    public Ranking calculateProvisionalRanking(String rankingId, String editionId, String categoryId, String roundId,
                                              List<Team> teams, Map<String, List<Attempt>> attemptsByTeamId) {
        RankingScope scope = RankingScope.of(rankingId, editionId, categoryId);
        RankingCalculationData data = RankingCalculationData.of(teams, attemptsByTeamId);
        return calculateProvisionalRanking(scope, roundId, data);
    }
}
