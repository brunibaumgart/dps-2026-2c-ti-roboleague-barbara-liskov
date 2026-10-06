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

    /**
     * How a rulebook scores: its base rules, its bonuses with the limit on their sum, and its deductions.
     */
    public record Scoring(List<RuleDefinition> rules, Bonuses bonuses, List<RuleDefinition> deductions) {
        public Scoring {
            rules = List.copyOf(Objects.requireNonNull(rules, "rules cannot be null"));
            Objects.requireNonNull(bonuses, "bonuses cannot be null");
            deductions = List.copyOf(Objects.requireNonNull(deductions, "deductions cannot be null"));
        }
    }

    /**
     * The bonus rules and the limit on their sum (F2), which only makes sense next to them.
     */
    public record Bonuses(List<RuleDefinition> rules, StrategyDefinition limit) {
        public Bonuses {
            rules = List.copyOf(Objects.requireNonNull(rules, "rules cannot be null"));
            Objects.requireNonNull(limit, "limit cannot be null");
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
