package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.ScoreRule.RuleEvaluation;

import java.util.List;
import java.util.Objects;

/**
 * What the rules of one source contributed to an attempt's score, before the bonus cap and the zero floor.
 */
public record SourceContribution(ResultSource source, RuleEvaluation evaluation) {
    public SourceContribution {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(evaluation, "evaluation cannot be null");
    }

    public List<ScoreItem> items() {
        return evaluation.items();
    }

    public double subtotal() {
        return evaluation.total();
    }
}
