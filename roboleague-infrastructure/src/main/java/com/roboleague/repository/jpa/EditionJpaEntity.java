package com.roboleague.repository.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;

/**
 * Table shape of an edition: its header and dates as columns, and its tournament, categories and registrations as
 * JSON. Never leaves this package: {@link EditionMapper} translates it to the domain aggregate.
 */
@Entity
@Table(name = "editions")
class EditionJpaEntity {

    @Id
    String id;
    @Version
    Long version;
    String name;
    int editionNumber;
    LocalDate startDate;
    LocalDate endDate;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    TournamentJson tournament;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    List<CategoryJson> categories;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    List<RegistrationJson> registrations;

    protected EditionJpaEntity() {
        // required by JPA
    }

    record TournamentJson(String id, String name, String description, String seasonId, int seasonYear,
                          String seasonName) {
    }

    /** A null {@code maxAge} means the category has no upper age limit. */
    record CategoryJson(String id, String name, int minMembers, int maxMembers, int minAge, Integer maxAge,
                        double maxWeightGrams, double maxLengthMm, double maxWidthMm, double maxHeightMm) {
    }

    /** The edition is the row itself; {@code teamId} is what {@link SpringDataEditions#findByTeamId} looks for. */
    record RegistrationJson(String teamId, String categoryId, String referenceDate, String registeredAt) {
    }
}
