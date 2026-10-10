package com.roboleague.ranking;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.scheme.ChallengeScore;
import com.roboleague.evaluation.scheme.RoundScore;
import com.roboleague.evaluation.scheme.RoundSelection;
import com.roboleague.scheduling.RoundId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A registered team with its attempts in one challenge and category, in the order their rounds were run.
 * Only attempts whose score counts take part: one awaiting a source or disqualified gives the team no round.
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
     * The team's challenge score with the rounds the selection keeps (F1). A round with several attempts counts
     * with its best one.
     */
    ScoredTeam scoredWith(RoundSelection selection) {
        Map<RoundId, Attempt> bestByRound = bestCountableByRound();
        List<RoundScore> rounds = new ArrayList<>();
        for (Attempt attempt : bestByRound.values()) {
            rounds.add(new RoundScore(attempt.getRoundId(), attempt.getLatestSnapshot().evaluation()));
        }
        ChallengeScore score = selection.select(rounds);
        TeamRounds teamRounds = new TeamRounds(results(score.considered(), bestByRound),
                results(score.discarded(), bestByRound), score.selectionRule());
        return new ScoredTeam(team, score, teamRounds);
    }

    private Map<RoundId, Attempt> bestCountableByRound() {
        Map<RoundId, Attempt> best = new LinkedHashMap<>();
        for (Attempt attempt : attempts) {
            if (attempt.countableScore().isPresent()) {
                best.merge(attempt.getRoundId(), attempt, (kept, other) -> totalOf(other) > totalOf(kept) ? other : kept);
            }
        }
        return best;
    }

    private static List<RoundResult> results(List<RoundScore> rounds, Map<RoundId, Attempt> bestByRound) {
        List<RoundResult> results = new ArrayList<>();
        for (RoundScore round : rounds) {
            results.add(new RoundResult(round.roundId(), bestByRound.get(round.roundId()).getId(), round.total()));
        }
        return results;
    }

    private static double totalOf(Attempt attempt) {
        return attempt.countableScore().orElseThrow().totalScore();
    }

    record ScoredTeam(TeamIdentity team, ChallengeScore score, TeamRounds rounds) {
    }
}
