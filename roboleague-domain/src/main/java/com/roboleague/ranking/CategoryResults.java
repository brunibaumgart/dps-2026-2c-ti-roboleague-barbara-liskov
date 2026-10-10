package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.scheduling.Round;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything a challenge has run in one category: how its current rulebook ranks, each registered team with its
 * attempts, and the rounds with their turns. The standings are calculated and their publication checked from it.
 */
public record CategoryResults(RankingScheme scheme, List<TeamAttempts> teams, List<Round> rounds) {
    public CategoryResults {
        Objects.requireNonNull(scheme, "scheme cannot be null");
        teams = List.copyOf(Objects.requireNonNull(teams, "teams cannot be null"));
        rounds = List.copyOf(Objects.requireNonNull(rounds, "rounds cannot be null"));
    }

    public StandingsTable table() {
        return StandingsTable.rank(scheme, teams);
    }

    public PublicationCheck publicationCheck() {
        return PublicationCheck.of(table(), rounds, attempts());
    }

    /**
     * The attempts of every registered team, team by team in the order of the rounds.
     */
    public List<Attempt> attempts() {
        List<Attempt> attempts = new ArrayList<>();
        for (TeamAttempts team : teams) {
            attempts.addAll(team.attempts());
        }
        return attempts;
    }
}
