package com.roboleague.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataAttempts extends JpaRepository<AttemptJpaEntity, String> {

    List<AttemptJpaEntity> findByTeamId(String teamId);

    List<AttemptJpaEntity> findByRoundId(String roundId);
}
