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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Gathers what a challenge has run in one category: the ranking scheme of its current rulebook, the teams
 * registered in the category with their attempts, and the rounds of the category in order. The challenge has to
 * exist and offer the category in its edition.
 */
public class CategoryResultsReader {
    private final ChallengeRepository challenges;
    private final EditionRepository editions;
    private final TeamRepository teams;
    private final RoundRepository rounds;
    private final AttemptRepository attempts;

    public CategoryResultsReader(ChallengeRepository challenges, EditionRepository editions, TeamRepository teams,
                                 RoundRepository rounds, AttemptRepository attempts) {
        this.challenges = Objects.requireNonNull(challenges, "challenges cannot be null");
        this.editions = Objects.requireNonNull(editions, "editions cannot be null");
        this.teams = Objects.requireNonNull(teams, "teams cannot be null");
        this.rounds = Objects.requireNonNull(rounds, "rounds cannot be null");
        this.attempts = Objects.requireNonNull(attempts, "attempts cannot be null");
    }

    public CategoryResults read(StandingsId id) {
        Objects.requireNonNull(id, "id cannot be null");
        Challenge challenge = challenges.findById(id.challengeId())
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + id.challengeId()));
        Edition edition = editions.findById(challenge.getEditionId())
                .orElseThrow(() -> new IllegalStateException("Edition of challenge " + challenge.getId()
                        + " not found: " + challenge.getEditionId()));
        edition.category(id.categoryId());
        List<Round> categoryRounds = rounds.findByChallengeId(id.challengeId()).stream()
                .filter(round -> round.getCategoryId().equals(id.categoryId()))
                .sorted(Comparator.comparingInt(Round::getRoundNumber))
                .toList();
        List<Attempt> runs = new ArrayList<>();
        for (Round round : categoryRounds) {
            runs.addAll(attempts.findByRoundId(round.getId()).stream()
                    .sorted(Comparator.comparingInt((Attempt attempt) -> attempt.getId().number())
                            .thenComparing(attempt -> attempt.getId().value()))
                    .toList());
        }
        List<TeamAttempts> registered = edition.getRegistrationsByCategory(id.categoryId()).stream()
                .map(Registration::teamId)
                .map(teamId -> new TeamAttempts(identity(teamId), attemptsOf(teamId, runs)))
                .toList();
        return new CategoryResults(challenge.currentRulebook().rankingScheme(), registered, categoryRounds);
    }

    private TeamIdentity identity(TeamId teamId) {
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new IllegalStateException("Registered team not found: " + teamId));
        return TeamIdentity.of(team.getId(), team.getName());
    }

    private static List<Attempt> attemptsOf(TeamId teamId, List<Attempt> runs) {
        return runs.stream().filter(attempt -> attempt.getTeamId().equals(teamId)).toList();
    }
}
