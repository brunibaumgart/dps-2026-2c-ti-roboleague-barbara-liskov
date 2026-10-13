package com.roboleague.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface SpringDataRounds extends JpaRepository<RoundJpaEntity, String> {

    List<RoundJpaEntity> findAllByOrderByIdAsc();

    List<RoundJpaEntity> findByChallengeIdOrderByIdAsc(String challengeId);

    Optional<RoundJpaEntity> findByChallengeIdAndEditionIdAndCategoryIdAndRoundNumber(
            String challengeId, String editionId, String categoryId, int roundNumber);

    /** The scope uniqueness Postgres enforces with rounds_scope_uk: challenge, category and number. */
    boolean existsByChallengeIdAndCategoryIdAndRoundNumberAndIdNot(
            String challengeId, String categoryId, int roundNumber, String id);

    /** The round holding the slot; the containment test uses the GIN index on slots. */
    @Query(value = "SELECT * FROM rounds"
            + " WHERE slots @> jsonb_build_array(jsonb_build_object('slotId', CAST(:slotId AS TEXT)))",
            nativeQuery = true)
    Optional<RoundJpaEntity> findBySlotId(String slotId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM rounds r, jsonb_array_elements(r.slots) slot"
            + " WHERE r.id <> :roundId AND slot ->> 'slotId' IN (:slotIds))", nativeQuery = true)
    boolean existsSlotInAnotherRound(String roundId, Collection<String> slotIds);
}
