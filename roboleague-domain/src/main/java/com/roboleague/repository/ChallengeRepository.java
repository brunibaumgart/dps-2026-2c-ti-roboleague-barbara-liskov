package com.roboleague.repository;

import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Stores challenges. {@link #save} throws {@link ConcurrentModificationConflict} when another writer created a
 * challenge with the same id first (Postgres adapter). Updates carry no version: the last write wins in every adapter.
 */
public interface ChallengeRepository {
    void save(Challenge challenge);
    Optional<Challenge> findById(ChallengeId id);
    /** The challenges with those ids, ordered by id; ids that do not exist are simply absent. */
    List<Challenge> findByIds(Collection<ChallengeId> ids);
    List<Challenge> findByEditionId(EditionId editionId);
}
