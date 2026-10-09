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
        storage.put(challenge.getId(), challenge);
    }

    @Override
    public Optional<Challenge> findById(ChallengeId id) {
        return Optional.ofNullable(storage.get(id));
    }
    @Override
    public List<Challenge> findByEditionId(EditionId editionId) {
        return storage.values().stream().filter(challenge -> challenge.getEditionId().equals(editionId))
                .sorted(Comparator.comparing(challenge -> challenge.getId().value())).toList();
    }
}
