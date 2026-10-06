package com.roboleague.repository.memory;

import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.Round;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRoundRepository implements RoundRepository {
    private final Map<String, Round> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Round round) {
        Objects.requireNonNull(round, "round cannot be null");
        storage.put(round.getId(), round);
    }

    @Override
    public Optional<Round> findBySlotId(String slotId) {
        return storage.values().stream()
                .filter(round -> round.slot(slotId).isPresent())
                .findFirst();
    }
}
