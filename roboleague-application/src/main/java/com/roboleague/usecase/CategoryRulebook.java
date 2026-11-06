package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.Rulebook;
import com.roboleague.repository.AttemptRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.Challenge;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Finds what a challenge played in one category and the rulebook the category plays with: the version its attempts
 * were scored with once it has results, the current one before ({@link Challenge#rulebookInForce}). Capture,
 * scheduling and standings use it, so a category plays and ranks with a single version.
 */
public class CategoryRulebook {
    private final RoundRepository rounds;
    private final AttemptRepository attempts;

    public CategoryRulebook(RoundRepository rounds, AttemptRepository attempts) {
        this.rounds = Objects.requireNonNull(rounds, "rounds cannot be null");
        this.attempts = Objects.requireNonNull(attempts, "attempts cannot be null");
    }

    /**
     * The rounds of the challenge in the category, by number.
     */
    public List<Round> roundsOf(Challenge challenge, CategoryId categoryId) {
        Objects.requireNonNull(challenge, "challenge cannot be null");
        Objects.requireNonNull(categoryId, "categoryId cannot be null");
        return rounds.findByChallengeId(challenge.getId()).stream()
                .filter(round -> round.getCategoryId().equals(categoryId))
                .sorted(Comparator.comparingInt(Round::getRoundNumber))
                .toList();
    }

    /**
     * The attempts of those rounds, round by round and, within a turn, in the order they were opened. One read of
     * attempts however many rounds there are.
     */
    public List<Attempt> attemptsOf(List<Round> categoryRounds) {
        Map<RoundId, Integer> position = new HashMap<>();
        for (Round round : categoryRounds) {
            position.putIfAbsent(round.getId(), position.size());
        }
        return attempts.findByRoundIds(position.keySet()).stream()
                .sorted(Comparator.comparingInt((Attempt attempt) -> position.get(attempt.getRoundId()))
                        .thenComparing(attempt -> attempt.getSlotId().value())
                        .thenComparingInt(attempt -> attempt.getId().number()))
                .toList();
    }

    public Rulebook inForce(Challenge challenge, CategoryId categoryId) {
        return inForce(challenge, attemptsOf(roundsOf(challenge, categoryId)));
    }

    /**
     * Stored results scored with two versions, or with one the challenge never published, are broken data: no
     * request can fix them.
     */
    static Rulebook inForce(Challenge challenge, List<Attempt> played) {
        try {
            return challenge.rulebookInForce(played.stream()
                    .map(attempt -> attempt.getRulebookReference().version())
                    .toList());
        } catch (IllegalStateException inconsistent) {
            throw new InconsistentStateException(inconsistent.getMessage());
        }
    }
}
