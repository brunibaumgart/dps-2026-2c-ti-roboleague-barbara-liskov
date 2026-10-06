package com.roboleague.repository.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * Table shape of an attempt: its turn, the rulebook version it is scored with and its stage as columns, and what
 * each source sent, its revisions and its events as JSON. Never leaves this package: {@link AttemptMapper}
 * translates it to the domain aggregate.
 */
@Entity
@Table(name = "attempts")
class AttemptJpaEntity {

    @Id
    String id;
    @Version
    Long version;
    String slotId;
    int attemptNumber;
    String roundId;
    String teamId;
    String challengeId;
    int rulebookVersion;
    String status;
    int openAppeals;
    String settledStatus;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    HistoryJson history;

    protected AttemptJpaEntity() {
        // required by JPA
    }

    record HistoryJson(List<DeliveryJson> deliveries, List<RevisionJson> revisions, List<EventJson> events) {
    }

    /** What one source sent, in the shape of the metrics it adds; what the other source measures stays empty. */
    record DeliveryJson(String source, String judgeId, MetricsJson sent) {
    }

    record RevisionJson(int number, String snapshotId, String authorId, String timestamp, String reason,
                        EvaluationJson evaluation) {
    }

    record EvaluationJson(int rulebookVersion, MetricsJson metrics, BreakdownJson breakdown) {
    }

    record BreakdownJson(SectionJson base, SectionJson bonuses, SectionJson deductions) {
    }

    record SectionJson(List<ItemJson> items, List<String> notes) {
    }

    record ItemJson(String concept, String rawMetric, String formula, double subtotal) {
    }

    /** One event of any kind; the fields it does not use stay null. */
    record EventJson(String type, String eventId, String timestamp, String actorId, String source, String reason,
                     Integer number, String appealId, String teamId, EvaluationJson evaluation) {
    }
}
