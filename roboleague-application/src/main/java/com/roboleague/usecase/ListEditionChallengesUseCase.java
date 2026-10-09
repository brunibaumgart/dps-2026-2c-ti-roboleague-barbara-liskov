package com.roboleague.usecase;

import com.roboleague.repository.ChallengeRepository;
import com.roboleague.repository.EditionRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.EditionId;

import java.util.List;
import java.util.Objects;

public class ListEditionChallengesUseCase {
    private final EditionRepository editions;
    private final ChallengeRepository challenges;
    public ListEditionChallengesUseCase(EditionRepository editions, ChallengeRepository challenges) {
        this.editions = Objects.requireNonNull(editions);
        this.challenges = Objects.requireNonNull(challenges);
    }

    public List<Challenge> execute(EditionId id) {
        editions.findById(id).orElseThrow(() -> new IllegalArgumentException("Edition not found: " + id));
        return challenges.findByEditionId(id);
    }
}
