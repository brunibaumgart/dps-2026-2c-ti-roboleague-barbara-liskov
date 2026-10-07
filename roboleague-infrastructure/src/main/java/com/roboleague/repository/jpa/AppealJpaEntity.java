package com.roboleague.repository.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Table shape of an appeal. Never leaves this package: {@link AppealMapper} translates it to the domain aggregate.
 */
@Entity
@Table(name = "appeals")
class AppealJpaEntity {

    @Id
    String id;
    String attemptId;
    String teamId;
    String reason;
    String evidenceDescription;
    String status;
    LocalDateTime submittedAt;
    String reviewerId;
    String resolutionNotes;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    MetricsJson revisedMetrics;
    LocalDateTime resolvedAt;

    protected AppealJpaEntity() {
        // required by JPA
    }
}
