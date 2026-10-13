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
 * Table shape of a round: its name, scope and status as columns, and its slots, each with its track, judges, time
 * window and track pause, as JSON. Never leaves this package: {@link RoundMapper} translates it to the aggregate.
 */
@Entity
@Table(name = "rounds")
class RoundJpaEntity {

    @Id
    String id;
    @Version
    Long version;
    String name;
    String challengeId;
    String editionId;
    String categoryId;
    int roundNumber;
    String status;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    List<SlotJson> slots;

    protected RoundJpaEntity() {
        // required by JPA
    }

    /** The round is the row itself; {@code slotId} is what {@link SpringDataRounds#findBySlotId} looks for. */
    record SlotJson(String slotId, String teamId, String status, String startTime, String endTime,
                    String trackInterval, TrackJson track, List<JudgeJson> judges) {
    }

    record TrackJson(String id, String name, String surfaceType, boolean active) {
    }

    record JudgeJson(String id, String fullName, String specialty, String certificationLevel) {
    }
}
