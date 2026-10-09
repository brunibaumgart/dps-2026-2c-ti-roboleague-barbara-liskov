package com.roboleague.repository.memory;

import com.roboleague.repository.RoundRepository;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.RoundScope;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.ChallengeId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRoundRepository implements RoundRepository {
    private final Map<RoundId, Round> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Round round) {
        Objects.requireNonNull(round, "round cannot be null");
        for (Round stored : storage.values()) {
            if (stored.getId().equals(round.getId())) { continue; }
            if (stored.getChallengeId().equals(round.getChallengeId())
                    && stored.getCategoryId().equals(round.getCategoryId())
                    && stored.getRoundNumber() == round.getRoundNumber()) {
                throw new IllegalStateException("Round scope already exists: " + round.getInfo().scope());
            }
            if (round.getSlots().stream().anyMatch(slot -> stored.slot(slot.getSlotId()).isPresent())) {
                throw new IllegalStateException("Slot id already belongs to another round");
            }
        }
        storage.put(round.getId(), round);
    }

    @Override
    public Optional<Round> findById(RoundId roundId) { return Optional.ofNullable(storage.get(roundId)); }

    @Override
    public Optional<Round> findByScope(RoundScope scope) {
        return storage.values().stream().filter(round -> round.getInfo().scope().equals(scope)).findFirst();
    }

    @Override
    public List<Round> findByChallengeId(ChallengeId challengeId) {
        return findAll().stream().filter(round -> round.getChallengeId().equals(challengeId)).toList();
    }

    @Override
    public List<Round> findAll() {
        return storage.values().stream().sorted(java.util.Comparator.comparing(round -> round.getId().value())).toList();
    }

    @Override
    public Optional<Round> findBySlotId(SlotId slotId) {
        return storage.values().stream()
                .filter(round -> round.slot(slotId).isPresent())
                .findFirst();
    }
}
