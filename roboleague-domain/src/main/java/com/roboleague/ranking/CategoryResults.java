package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.scheduling.Round;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything a challenge has run in one category: the rulebook the category plays with, each registered team with
 * its attempts, and the rounds with their turns. The standings are calculated and their publication checked from it.
 * Every attempt was scored with that rulebook, so the table ranks with the same version that scored its rounds.
 */
public record CategoryResults(Rulebook rulebook, List<TeamAttempts> teams, List<Round> rounds) {
    public CategoryResults {
        Objects.requireNonNull(rulebook, "rulebook cannot be null");
        teams = List.copyOf(Objects.requireNonNull(teams, "teams cannot be null"));
        rounds = List.copyOf(Objects.requireNonNull(rounds, "rounds cannot be null"));
        for (TeamAttempts team : teams) {
            for (Attempt attempt : team.attempts()) {
                if (!attempt.getRulebookReference().version().equals(rulebook.version())) {
                    throw new IllegalArgumentException("attempt " + attempt.getId() + " was scored with "
                            + attempt.getRulebookReference() + ", not with v" + rulebook.version().number());
                }
            }
        }
    }

    public StandingsTable table() {
        return StandingsTable.rank(rulebook, teams);
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
