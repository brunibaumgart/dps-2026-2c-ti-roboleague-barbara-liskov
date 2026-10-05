package com.roboleague.evaluation.definition;

import java.util.List;
import java.util.Objects;

/**
 * Description of a whole rulebook: what it measures, how it scores and how it ranks.
 */
public record RulebookDefinition(List<MetricDeclaration> metrics, Scoring scoring, Ranking ranking) {
    public RulebookDefinition {
        metrics = List.copyOf(Objects.requireNonNull(metrics, "metrics cannot be null"));
        Objects.requireNonNull(scoring, "scoring cannot be null");
        Objects.requireNonNull(ranking, "ranking cannot be null");
    }

    public record Scoring(List<RuleDefinition> rules, List<RuleDefinition> bonuses, StrategyDefinition bonusLimit) {
        public Scoring {
            rules = List.copyOf(Objects.requireNonNull(rules, "rules cannot be null"));
            bonuses = List.copyOf(Objects.requireNonNull(bonuses, "bonuses cannot be null"));
            Objects.requireNonNull(bonusLimit, "bonusLimit cannot be null");
        }
    }

    public record Ranking(StrategyDefinition roundSelection, List<String> criteria) {
        public Ranking {
            Objects.requireNonNull(roundSelection, "roundSelection cannot be null");
            Objects.requireNonNull(criteria, "criteria cannot be null");
            if (criteria.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("a tie-break criterion cannot be empty");
            }
            criteria = List.copyOf(criteria);
        }
    }
}
