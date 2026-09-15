package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreBreakdown;
import com.roboleague.evaluation.ScoreItem;

import java.util.List;

/**
 * Strategy interface for scoring rules.
 */
public interface ScoreRule {

    record RuleEvaluation(List<ScoreItem> items, List<String> notes) {
        public RuleEvaluation {
            items = items != null ? List.copyOf(items) : List.of();
            notes = notes != null ? List.copyOf(notes) : List.of();
        }

        public static RuleEvaluation of(ScoreItem item) {
            return new RuleEvaluation(List.of(item), List.of());
        }

        public static RuleEvaluation of(ScoreItem item, String note) {
            return new RuleEvaluation(List.of(item), List.of(note));
        }

        public static RuleEvaluation empty() {
            return new RuleEvaluation(List.of(), List.of());
        }
    }

    String getRuleName();

    RuleEvaluation evaluate(RawMetrics metrics);
}
