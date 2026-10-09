package com.roboleague.usecase;

import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class QueryRoundsUseCase {
    private final ChallengeRepository challenges;
    private final EditionRepository editions;
    private final RoundRepository rounds;
    public QueryRoundsUseCase(ChallengeRepository challenges, EditionRepository editions, RoundRepository rounds) {
        this.challenges = Objects.requireNonNull(challenges);
        this.editions = Objects.requireNonNull(editions);
        this.rounds = Objects.requireNonNull(rounds);
    }

    public Round get(RoundId id) {
        return rounds.findById(id).orElseThrow(() -> new IllegalArgumentException("Round not found: " + id));
    }

    public List<Round> list(ChallengeId id, Optional<CategoryId> category) {
        var challenge = challenges.findById(id).orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + id));
        var edition = editions.findById(challenge.getEditionId())
                .orElseThrow(() -> new IllegalArgumentException("Edition not found: " + challenge.getEditionId()));
        category.ifPresent(edition::category);
        return rounds.findByChallengeId(id).stream()
                .filter(round -> category.isEmpty() || category.get().equals(round.getCategoryId()))
                .sorted(Comparator.comparingInt(Round::getRoundNumber)
                        .thenComparing(round -> round.getCategoryId().value()).thenComparing(round -> round.getId().value()))
                .toList();
    }
}
