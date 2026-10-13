package com.roboleague.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface SpringDataEditions extends JpaRepository<EditionJpaEntity, String> {

    List<EditionJpaEntity> findAllByOrderByIdAsc();

    /** Editions with a registration of the team; the containment test uses the GIN index on registrations. */
    @Query(value = "SELECT * FROM editions"
            + " WHERE registrations @> jsonb_build_array(jsonb_build_object('teamId', CAST(:teamId AS TEXT)))"
            + " ORDER BY id", nativeQuery = true)
    List<EditionJpaEntity> findByTeamId(String teamId);
}
