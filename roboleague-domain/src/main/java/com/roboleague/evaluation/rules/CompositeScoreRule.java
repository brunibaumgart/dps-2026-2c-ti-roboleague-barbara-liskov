package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreBreakdown;
import com.roboleague.evaluation.ScoreItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Composite score rule aggregating multiple child scoring rules.
 */
public class CompositeScoreRule implements ScoreRule {
    private final String name;
    private final List<ScoreRule> rules;

    public CompositeScoreRule(String name, List<ScoreRule> rules) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.rules = rules != null ? new ArrayList<>(rules) : new ArrayList<>();
    }

    public CompositeScoreRule(String name) {
        this(name, new ArrayList<>());
    }

    public void addRule(ScoreRule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");
        this.rules.add(rule);
    }

    public List<ScoreRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    @Override
    public String getRuleName() {
        return name;
    }

    @Override
    public RuleEvaluation evaluate(RawMetrics metrics) {
        List<ScoreItem> allItems = new ArrayList<>();
        List<String> allNotes = new ArrayList<>();

        for (ScoreRule rule : rules) {
            RuleEvaluation eval = rule.evaluate(metrics);
            allItems.addAll(eval.items());
            allNotes.addAll(eval.notes());
        }

        return new RuleEvaluation(allItems, allNotes);
    }

    public ScoreBreakdown evaluateBreakdown(RawMetrics metrics) {
        RuleEvaluation eval = evaluate(metrics);
        return ScoreBreakdown.of(eval.items(), eval.notes());
    }
}
