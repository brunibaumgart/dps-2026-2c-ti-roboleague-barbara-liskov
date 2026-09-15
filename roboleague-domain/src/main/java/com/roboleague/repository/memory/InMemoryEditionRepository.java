package com.roboleague.repository.memory;

import com.roboleague.repository.EditionRepository;
import com.roboleague.tournament.Edition;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryEditionRepository implements EditionRepository {
    private final Map<String, Edition> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Edition edition) {
        Objects.requireNonNull(edition, "edition cannot be null");
        storage.put(edition.getId(), edition);
    }

    @Override
    public Optional<Edition> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Edition> findAll() {
        return new ArrayList<>(storage.values());
    }
}
