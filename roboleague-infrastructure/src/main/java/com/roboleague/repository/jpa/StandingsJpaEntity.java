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
 * Table shape of the standings of a challenge in a category: the challenge and the category as columns, and every
 * version with its rows and every publication as JSON. Never leaves this package: {@link StandingsMapper}
 * translates it to the domain aggregate.
 */
@Entity
@Table(name = "standings")
class StandingsJpaEntity {

    @Id
    String id;
    @Version
    Long version;
    String challengeId;
    String categoryId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    List<VersionJson> versions;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    List<PublicationJson> publications;

    protected StandingsJpaEntity() {
        // required by JPA
    }

    record VersionJson(int number, String calculatedAt, List<EntryJson> entries) {
    }

    record EntryJson(int position, String explanation, String teamId, String teamName, List<RoundJson> considered,
                     List<RoundJson> discarded, String selectionRule) {
    }

    record RoundJson(String roundId, String attemptId, double total) {
    }

    record PublicationJson(int version, String publishedAt, String authorId, String reason) {
    }
}
