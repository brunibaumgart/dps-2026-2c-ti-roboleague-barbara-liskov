package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.ranking.CategoryResults;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.TeamAttempts;
import com.roboleague.ranking.TeamIdentity;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.Registration;
import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;

import java.util.List;
import java.util.Objects;

/**
 * Gathers what a challenge has run in one category: the rulebook the category plays with (the version that scored
 * its attempts, see {@link CategoryRulebook}), the teams registered in the category with their attempts, and the
 * rounds of the category in order. The challenge has to exist and offer the category in its edition.
 */
public class CategoryResultsReader {
    private final ChallengeRepository challenges;
    private final EditionRepository editions;
    private final TeamRepository teams;
    private final CategoryRulebook played;

    public CategoryResultsReader(ChallengeRepository challenges, EditionRepository editions, TeamRepository teams,
                                 RoundRepository rounds, AttemptRepository attempts) {
        this.challenges = Objects.requireNonNull(challenges, "challenges cannot be null");
        this.editions = Objects.requireNonNull(editions, "editions cannot be null");
        this.teams = Objects.requireNonNull(teams, "teams cannot be null");
        this.played = new CategoryRulebook(rounds, attempts);
    }

    public CategoryResults read(StandingsId id) {
        Objects.requireNonNull(id, "id cannot be null");
        Challenge challenge = challenges.findById(id.challengeId())
                .orElseThrow(() -> new NotFoundException("Challenge", id.challengeId()));
        Edition edition = editions.findById(challenge.getEditionId())
                .orElseThrow(() -> new InconsistentStateException("Edition of challenge " + challenge.getId()
                        + " not found: " + challenge.getEditionId()));
        edition.category(id.categoryId());
        List<Round> categoryRounds = played.roundsOf(challenge, id.categoryId());
        List<Attempt> runs = played.attemptsOf(categoryRounds);
        List<TeamAttempts> registered = edition.getRegistrationsByCategory(id.categoryId()).stream()
                .map(Registration::teamId)
                .map(teamId -> new TeamAttempts(identity(teamId), attemptsOf(teamId, runs)))
                .toList();
        return new CategoryResults(CategoryRulebook.inForce(challenge, runs), registered, categoryRounds);
    }

    private TeamIdentity identity(TeamId teamId) {
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new InconsistentStateException("Registered team not found: " + teamId));
        return TeamIdentity.of(team.getId(), team.getName());
    }

    private static List<Attempt> attemptsOf(TeamId teamId, List<Attempt> runs) {
        return runs.stream().filter(attempt -> attempt.getTeamId().equals(teamId)).toList();
    }
}
