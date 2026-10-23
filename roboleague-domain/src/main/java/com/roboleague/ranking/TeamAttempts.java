package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.scheme.ChallengeScore;
import com.roboleague.evaluation.scheme.RankingScheme;
import com.roboleague.evaluation.scheme.RoundScore;
import com.roboleague.scheduling.RoundId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A registered team with its attempts in one challenge and category, in the order their rounds were run and, within
 * a turn, in the order the attempts were opened. Only attempts whose score counts take part: one awaiting a source
 * or disqualified gives the team no round.
 */
public record TeamAttempts(TeamIdentity team, List<Attempt> attempts) {
    public TeamAttempts {
        Objects.requireNonNull(team, "team cannot be null");
        attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts cannot be null"));
        for (Attempt attempt : attempts) {
            if (!attempt.getTeamId().equals(team.teamId())) {
                throw new IllegalArgumentException("attempt " + attempt.getId() + " belongs to "
                        + attempt.getTeamId() + ", not to " + team.teamId());
            }
        }
    }

    /**
     * The team's challenge score with the scheme of the rulebook (F1): each round counts with the attempt its
     * attempt selection chooses, and the round selection keeps the rounds that count.
     */
    ScoredTeam scoredWith(RankingScheme scheme) {
        Map<RoundId, Attempt> countedByRound = new LinkedHashMap<>();
        Map<RoundId, List<Attempt>> byRound = byRound();
        byRound.forEach((roundId, turn) -> scheme.attemptSelection().counted(turn)
                .ifPresent(attempt -> countedByRound.put(roundId, attempt)));
        List<RoundScore> rounds = new ArrayList<>();
        for (Attempt attempt : countedByRound.values()) {
            rounds.add(new RoundScore(attempt.getRoundId(), attempt.getLatestSnapshot().evaluation()));
        }
        ChallengeScore score = scheme.roundSelection().select(rounds);
        TeamRounds teamRounds = new TeamRounds(results(score.considered(), countedByRound, byRound),
                results(score.discarded(), countedByRound, byRound), score.selectionRule(),
                scheme.attemptSelection().describe());
        return new ScoredTeam(team, score, teamRounds);
    }

    /**
     * The attempts of each round, in the order the turn opened them.
     */
    private Map<RoundId, List<Attempt>> byRound() {
        Map<RoundId, List<Attempt>> byRound = new LinkedHashMap<>();
        for (Attempt attempt : attempts) {
            byRound.computeIfAbsent(attempt.getRoundId(), round -> new ArrayList<>()).add(attempt);
        }
        byRound.values().forEach(turn -> turn.sort(Comparator.comparingInt(attempt -> attempt.getId().number())));
        return byRound;
    }

    private static List<RoundResult> results(List<RoundScore> rounds, Map<RoundId, Attempt> countedByRound,
                                             Map<RoundId, List<Attempt>> byRound) {
        List<RoundResult> results = new ArrayList<>();
        for (RoundScore round : rounds) {
            Attempt counted = countedByRound.get(round.roundId());
            List<AttemptId> others = byRound.get(round.roundId()).stream()
                    .filter(attempt -> attempt != counted)
                    .map(Attempt::getId)
                    .toList();
            results.add(new RoundResult(round.roundId(), counted.getId(), round.total(), others));
        }
        return results;
    }

    record ScoredTeam(TeamIdentity team, ChallengeScore score, TeamRounds rounds) {
    }
}
