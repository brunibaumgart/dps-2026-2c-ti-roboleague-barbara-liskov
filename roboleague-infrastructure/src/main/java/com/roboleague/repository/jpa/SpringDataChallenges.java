package com.roboleague.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataChallenges extends JpaRepository<ChallengeJpaEntity, String> {
    List<ChallengeJpaEntity> findByEditionIdOrderByIdAsc(String editionId);
}
