package com.roboleague.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataAppeals extends JpaRepository<AppealJpaEntity, String> {

    List<AppealJpaEntity> findByAttemptId(String attemptId);

    List<AppealJpaEntity> findByStatus(String status);
}
