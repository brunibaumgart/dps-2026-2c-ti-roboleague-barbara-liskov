package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ResultSource;

import java.util.List;
import java.util.Objects;

/**
 * Composite score rule aggregating child rules that read from the same source (e.g. "Desempeño en pista").
 */
public class CompositeScoreRule implements ScoreRule {
    private final String name;
    private final List<ScoreRule> rules;
    private final ResultSource source;

    public CompositeScoreRule(String name, List<ScoreRule> rules) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules cannot be null"));
        if (this.rules.isEmpty()) {
            throw new IllegalArgumentException("a composite rule needs at least one rule");
        }
        List<ResultSource> sources = this.rules.stream().map(ScoreRule::source).distinct().toList();
        if (sources.size() != 1) {
            throw new IllegalArgumentException("a composite rule needs rules from exactly one source: " + sources);
        }
        this.source = sources.getFirst();
    }

    public List<ScoreRule> getRules() {
        return rules;
    }

    @Override
    public String getRuleName() {
        return name;
    }

    @Override
    public ResultSource source() {
        return source;
    }

    @Override
    public RuleEvaluation evaluate(RawMetrics metrics) {
        return RuleEvaluation.combining(rules, metrics);
    }
}
