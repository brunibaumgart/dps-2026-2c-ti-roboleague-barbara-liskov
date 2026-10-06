package com.roboleague.repository;

import com.roboleague.scheduling.Round;

import java.util.Optional;

public interface RoundRepository {
    void save(Round round);
    Optional<Round> findBySlotId(String slotId);
}
