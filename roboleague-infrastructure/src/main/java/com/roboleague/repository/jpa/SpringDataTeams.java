package com.roboleague.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataTeams extends JpaRepository<TeamJpaEntity, String> {

    List<TeamJpaEntity> findAllByOrderByIdAsc();
}
