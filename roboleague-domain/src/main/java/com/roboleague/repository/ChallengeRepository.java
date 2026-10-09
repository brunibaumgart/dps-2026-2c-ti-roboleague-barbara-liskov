package com.roboleague.repository;

import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.EditionId;

import java.util.List;
import java.util.Optional;

public interface ChallengeRepository {
    void save(Challenge challenge);
    Optional<Challenge> findById(ChallengeId id);
    List<Challenge> findByEditionId(EditionId editionId);
}
