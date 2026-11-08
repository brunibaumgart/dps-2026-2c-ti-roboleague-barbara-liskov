package com.roboleague.usecase;

import com.roboleague.port.in.QueryTeamResults;
import com.roboleague.port.in.InconsistentStateException;
import com.roboleague.port.in.NotFoundException;
import com.roboleague.port.in.TeamTurn;

import com.roboleague.evaluation.Attempt;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.repository.TeamRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.Slot;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.TeamId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a team did in a challenge: each of its turns, round by round, with the attempts it ran. Reads only.
 */
public class QueryTeamResultsUseCase implements QueryTeamResults {
    private final TeamRepository teams;
    private final ChallengeRepository challenges;
    private final EditionRepository editions;
    private final RoundRepository rounds;
    private final AttemptRepository attempts;

    public QueryTeamResultsUseCase(TeamRepository teams, ChallengeRepository challenges, EditionRepository editions,
                                   RoundRepository rounds, AttemptRepository attempts) {
        this.teams = Objects.requireNonNull(teams, "teams cannot be null");
        this.challenges = Objects.requireNonNull(challenges, "challenges cannot be null");
        this.editions = Objects.requireNonNull(editions, "editions cannot be null");
        this.rounds = Objects.requireNonNull(rounds, "rounds cannot be null");
        this.attempts = Objects.requireNonNull(attempts, "attempts cannot be null");
    }

    /**
     * Every turn the team has in the challenge (optionally in one category), in round order, with its attempts in
     * the order they were opened. Reads: the team (1), the challenge (1), its edition when a category is given (1),
     * the challenge's rounds (1) and the team's attempts (1), so 4 or 5 however many rounds or attempts there are.
     */
    public List<TeamTurn> execute(TeamId teamId, ChallengeId challengeId, Optional<CategoryId> category) {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(challengeId, "challengeId cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        teams.findById(teamId).orElseThrow(() -> new NotFoundException("Team", teamId));
        Challenge challenge = challenges.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Challenge", challengeId));
        if (category.isPresent()) {
            var edition = editions.findById(challenge.getEditionId())
                    .orElseThrow(() -> new InconsistentStateException("Edition of challenge " + challengeId
                            + " not found: " + challenge.getEditionId()));
            edition.category(category.get());
        }
        List<Round> teamRounds = rounds.findByChallengeId(challengeId).stream()
                .filter(round -> category.isEmpty() || category.get().equals(round.getCategoryId()))
                .filter(round -> round.getSlots().stream().anyMatch(slot -> slot.getTeamId().equals(teamId)))
                .sorted(Comparator.comparingInt(Round::getRoundNumber)
                        .thenComparing(round -> round.getCategoryId().value())
                        .thenComparing(round -> round.getId().value()))
                .toList();
        Set<RoundId> roundIds = teamRounds.stream().map(Round::getId).collect(Collectors.toSet());
        Map<SlotId, List<Attempt>> bySlot = attempts.findByTeamId(teamId).stream()
                .filter(attempt -> roundIds.contains(attempt.getRoundId()))
                .sorted(Comparator.comparingInt(attempt -> attempt.getId().number()))
                .collect(Collectors.groupingBy(Attempt::getSlotId));
        return teamRounds.stream()
                .flatMap(round -> round.getSlots().stream()
                        .filter(slot -> slot.getTeamId().equals(teamId))
                        .map(slot -> turn(round, slot, bySlot)))
                .toList();
    }

    private static TeamTurn turn(Round round, Slot slot, Map<SlotId, List<Attempt>> bySlot) {
        return new TeamTurn(round, slot, bySlot.getOrDefault(slot.getSlotId(), List.of()));
    }
}
