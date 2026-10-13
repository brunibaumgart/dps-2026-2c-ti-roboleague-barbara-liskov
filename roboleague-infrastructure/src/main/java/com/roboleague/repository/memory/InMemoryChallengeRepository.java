package com.roboleague.repository.memory;

import com.roboleague.repository.ChallengeRepository;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryChallengeRepository implements ChallengeRepository {
    private final Map<ChallengeId, Challenge> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Challenge challenge) {
        Objects.requireNonNull(challenge, "challenge cannot be null");
        storage.put(challenge.getId(), copy(challenge));
    }

    @Override
    public Optional<Challenge> findById(ChallengeId id) {
        return Optional.ofNullable(storage.get(id)).map(InMemoryChallengeRepository::copy);
    }

    @Override
    public List<Challenge> findByEditionId(EditionId editionId) {
        return storage.values().stream().filter(challenge -> challenge.getEditionId().equals(editionId))
                .sorted(Comparator.comparing(challenge -> challenge.getId().value()))
                .map(InMemoryChallengeRepository::copy).toList();
    }

    /**
     * Stores and hands out copies, like a database would: a use case that changes a challenge and forgets to save it
     * leaves the stored one as it was, so its test fails.
     */
    private static Challenge copy(Challenge challenge) {
        return Challenge.restore(Challenge.draft(challenge.getId(), challenge.getEditionId(), challenge.getName()),
                challenge.rulebooks());
    }
}
