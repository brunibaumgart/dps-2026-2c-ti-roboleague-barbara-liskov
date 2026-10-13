package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.scheduling.Round;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything a challenge has run in one category: its current rulebook, which ranks, each registered team with its
 * attempts, and the rounds with their turns. The standings are calculated and their publication checked from it.
 */
public record CategoryResults(Rulebook rulebook, List<TeamAttempts> teams, List<Round> rounds) {
    public CategoryResults {
        Objects.requireNonNull(rulebook, "rulebook cannot be null");
        teams = List.copyOf(Objects.requireNonNull(teams, "teams cannot be null"));
        rounds = List.copyOf(Objects.requireNonNull(rounds, "rounds cannot be null"));
    }

    public StandingsTable table() {
        return StandingsTable.rank(rulebook.version(), rulebook.rankingScheme(), teams);
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
