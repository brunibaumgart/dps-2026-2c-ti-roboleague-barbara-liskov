package com.roboleague.repository.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

/**
 * Table shape of a challenge. Every rulebook version is stored as a JSON description that
 * {@link ChallengeMapper} rebuilds through the rule catalog. Never leaves this package.
 */
@Entity
@Table(name = "challenges")
class ChallengeJpaEntity {

    @Id
    String id;
    String editionId;
    String name;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    RulebooksJson rulebooks;

    protected ChallengeJpaEntity() {
        // required by JPA
    }

    record RulebooksJson(List<RulebookJson> versions) {
    }

    record RulebookJson(int version, List<MetricDeclarationJson> metrics, ScoringJson scoring, RankingJson ranking) {
    }

    record MetricDeclarationJson(String name, String source, String unit, Map<String, Double> range) {
    }

    record ScoringJson(List<RuleJson> rules, List<RuleJson> bonuses, List<RuleJson> deductions,
                       StrategyJson bonusLimit) {
    }

    record RankingJson(StrategyJson roundSelection, List<String> criteria) {
    }

    record RuleJson(String type, String name, Map<String, Double> numbers, Map<String, MetricJson> metrics,
                    List<RuleJson> rules) {
    }

    record MetricJson(String name, String source) {
    }

    record StrategyJson(String type, Map<String, Double> numbers) {
    }
}
