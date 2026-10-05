package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.ScoreItem;

import java.util.ArrayList;
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

        public double total() {
            double total = 0.0;
            for (ScoreItem item : items) {
                total += item.subtotal();
            }
            return total;
        }

        public static RuleEvaluation combining(List<ScoreRule> rules, RawMetrics metrics) {
            List<ScoreItem> items = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            for (ScoreRule rule : rules) {
                RuleEvaluation evaluation = rule.evaluate(metrics);
                items.addAll(evaluation.items());
                notes.addAll(evaluation.notes());
            }
            return new RuleEvaluation(items, notes);
        }
    }

    String getRuleName();

    ResultSource source();

    RuleEvaluation evaluate(RawMetrics metrics);
}
