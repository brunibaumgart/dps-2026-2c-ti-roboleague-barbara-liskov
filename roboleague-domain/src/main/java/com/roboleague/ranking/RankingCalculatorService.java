package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.ranking.tiebreakers.TieBreakerChain;
import com.roboleague.tournament.Team;

import java.util.*;

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

    public Ranking calculateProvisionalRanking(String rankingId, String editionId, String categoryId, String roundId,
                                              List<Team> teams, Map<String, List<Attempt>> attemptsByTeamId) {
        Objects.requireNonNull(rankingId, "rankingId cannot be null");
        Objects.requireNonNull(teams, "teams cannot be null");

        List<TeamScore> teamScores = new ArrayList<>();
        for (Team team : teams) {
            List<Attempt> teamAttempts = attemptsByTeamId.getOrDefault(team.getId(), List.of());
            TeamScore score = TeamScore.fromBestAttempt(
                    team.getId(),
                    team.getName(),
                    categoryId,
                    editionId,
                    teamAttempts
            );
            teamScores.add(score);
        }

        // Sort according to the tie-breaking comparator chain
        teamScores.sort(tieBreakerChain);

        List<RankingEntry> entries = new ArrayList<>();
        int currentRank = 1;

        for (int i = 0; i < teamScores.size(); i++) {
            TeamScore current = teamScores.get(i);
            boolean tiedWithPrev = false;
            String explanation = "Posición asignada por criterios estándar";

            if (i > 0) {
                TeamScore prev = teamScores.get(i - 1);
                // If compare returns 0 on score, time, penalties and judges, it's a tie
                if (current.totalScore() == prev.totalScore()
                        && current.bestAttemptTime() == prev.bestAttemptTime()
                        && current.totalPenalties() == prev.totalPenalties()
                        && current.judgeSubjectiveScore() == prev.judgeSubjectiveScore()) {
                    tiedWithPrev = true;
                    explanation = "Empate técnico con posición previa en puntaje, tiempo, faltas y jueces";
                } else {
                    currentRank = i + 1;
                }
            }

            entries.add(new RankingEntry(currentRank, current, tiedWithPrev, explanation));
        }

        return new Ranking(rankingId, editionId, categoryId, roundId, entries);
    }
}
