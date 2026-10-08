package com.roboleague.repository;

import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.SlotId;

import java.util.Optional;

public interface RoundRepository {
    void save(Round round);
    Optional<Round> findBySlotId(SlotId slotId);
}
