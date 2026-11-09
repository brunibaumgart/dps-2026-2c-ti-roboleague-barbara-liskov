package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.ScoreItem;
import com.roboleague.evaluation.ValueRange;
import com.roboleague.evaluation.definition.RuleDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        public Points total() {
            Points total = Points.ZERO;
            for (ScoreItem item : items) {
                total = total.plus(item.subtotal());
            }
            return total;
        }

        public static RuleEvaluation combining(List<? extends ScoreRule> rules, RawMetrics metrics) {
            List<RuleEvaluation> evaluations = new ArrayList<>();
            for (ScoreRule rule : rules) {
                evaluations.add(rule.evaluate(metrics));
            }
            return concat(evaluations);
        }

        public static RuleEvaluation concat(List<RuleEvaluation> evaluations) {
            List<ScoreItem> items = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            for (RuleEvaluation evaluation : evaluations) {
                items.addAll(evaluation.items());
                notes.addAll(evaluation.notes());
            }
            return new RuleEvaluation(items, notes);
        }
    }

    String getRuleName();

    ResultSource source();

    /**
     * Named measurements this rule reads, which its rulebook has to declare. Time, objectives, penalties,
     * consumption and judge scores are fixed fields of the captured metrics, not named metrics.
     */
    Set<Metric> metrics();

    /**
     * The values this rule can score for each metric it bounds (a precision between 0 and 1, rescued victims up
     * to those placed). The rulebook rejects at publication a declaration wider than this, so a capture inside
     * the declared range can always be scored; a rule with no bound of its own declares nothing.
     */
    default Map<Metric, ValueRange> acceptedValues() {
        return Map.of();
    }

    /**
     * How this rule is described to be stored or sent through the API; {@code RuleCatalog} rebuilds it.
     */
    RuleDefinition definition();

    RuleEvaluation evaluate(RawMetrics metrics);
}
