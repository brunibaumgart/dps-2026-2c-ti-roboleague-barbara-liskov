package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.ScoreItem;

import java.util.Locale;
import java.util.Objects;

/**
 * Grants a fixed bonus when a milestone is reached, and nothing otherwise.
 */
public final class MilestoneBonusRule implements ScoreRule {
    private final String ruleName;
    private final Milestone milestone;
    private final double bonus;

    public MilestoneBonusRule(String ruleName, Milestone milestone, double bonus) {
        if (bonus < 0) {
            throw new IllegalArgumentException("bonus cannot be negative");
        }
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.milestone = Objects.requireNonNull(milestone, "milestone cannot be null");
        this.bonus = bonus;
    }

    @Override
    public ResultSource source() {
        return milestone.metric().source();
    }

    @Override
    public String getRuleName() {
        return ruleName;
    }

    @Override
    public RuleEvaluation evaluate(RawMetrics metrics) {
        double measured = metrics.measurement(milestone.metric());
        boolean reached = milestone.reachedWith(measured);
        double subtotal = reached ? bonus : 0.0;

        ScoreItem item = ScoreItem.of(
                ruleName,
                String.format(Locale.US, "%s = %.2f (umbral %.2f)", milestone.metric().name(), measured, milestone.threshold()),
                reached
                        ? String.format(Locale.US, "hito alcanzado: +%.1f pts", bonus)
                        : "hito no alcanzado: 0 pts",
                subtotal
        );
        return RuleEvaluation.of(item, String.format(Locale.US, "%s: %s", ruleName, reached ? "alcanzado" : "no alcanzado"));
    }
}
