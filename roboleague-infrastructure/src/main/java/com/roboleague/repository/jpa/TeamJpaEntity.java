package com.roboleague.repository.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

/**
 * Table shape of a team: its profile as columns, and its robot, members and documentation as JSON. Never leaves
 * this package: {@link TeamMapper} translates it to the domain aggregate.
 */
@Entity
@Table(name = "teams")
class TeamJpaEntity {

    @Id
    String id;
    @Version
    Long version;
    String name;
    String institution;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    RobotJson robot;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    List<MemberJson> members;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    DocumentationJson documentation;

    protected TeamJpaEntity() {
        // required by JPA
    }

    record RobotJson(String id, String name, double weightGrams, double lengthMm, double widthMm, double heightMm,
                     int actuatorCount, List<String> sensors) {
    }

    record MemberJson(String id, String fullName, String birthDate, String role) {
    }

    /** Verification facts as recorded; {@code verifiedAt} and {@code verifiedBy} stay null while unverified. */
    record DocumentationJson(Map<String, String> documents, boolean verified, String verifiedAt, String verifiedBy,
                             String revocationReason) {
    }
}
