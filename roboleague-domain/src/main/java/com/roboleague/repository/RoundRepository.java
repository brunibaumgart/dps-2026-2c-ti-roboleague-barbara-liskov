package com.roboleague.repository;

import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.RoundScope;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.ChallengeId;

import java.util.List;
import java.util.Optional;

/** Stores the entire aggregate. Scheduling exclusion across concurrent requests needs adapter-level coordination. */
public interface RoundRepository {
    void save(Round round);
    Optional<Round> findById(RoundId roundId);
    Optional<Round> findByScope(RoundScope scope);
    List<Round> findByChallengeId(ChallengeId challengeId);
    List<Round> findAll();
    Optional<Round> findBySlotId(SlotId slotId);
}
