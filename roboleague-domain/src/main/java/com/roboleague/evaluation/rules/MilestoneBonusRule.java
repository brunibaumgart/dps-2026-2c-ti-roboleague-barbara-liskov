package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.RuleArguments;
import com.roboleague.evaluation.definition.RuleDefinition;
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

    public static final String TYPE = "milestone";
    private static final String THRESHOLD = "threshold";
    private static final String BONUS = "bonus";
    private static final String METRIC = "metric";

    public static MilestoneBonusRule from(RuleDefinition definition) {
        RuleArguments arguments = definition.arguments();
        return new MilestoneBonusRule(definition.name(),
                new Milestone(arguments.metric(METRIC), arguments.numbers().number(THRESHOLD)),
                arguments.numbers().number(BONUS));
    }

    @Override
    public RuleDefinition definition() {
        return new RuleDefinition(TYPE, ruleName, RuleArguments.of(Parameters.none()
                        .with(THRESHOLD, milestone.threshold())
                        .with(BONUS, bonus))
                .withMetric(METRIC, milestone.metric()));
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
