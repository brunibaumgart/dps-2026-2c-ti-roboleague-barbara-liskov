package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.RuleArguments;
import com.roboleague.evaluation.definition.RuleDefinition;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.ScoreItem;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Adds points for each rescued victim. The deduction for the ones left behind is a separate rule
 * ({@link AbandonedVictimsRule}), so what is rewarded and what is deducted are told apart.
 */
public final class VictimsRule implements ScoreRule {
    private final String ruleName;
    private final Metric rescued;
    private final double pointsPerRescued;

    public VictimsRule(String ruleName, Metric rescued, double pointsPerRescued) {
        if (pointsPerRescued < 0) {
            throw new IllegalArgumentException("pointsPerRescued cannot be negative");
        }
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.rescued = Objects.requireNonNull(rescued, "rescued cannot be null");
        this.pointsPerRescued = pointsPerRescued;
    }

    public static final String TYPE = "victims";
    private static final String POINTS_PER_RESCUED = "pointsPerRescued";
    private static final String RESCUED = "rescued";

    public static VictimsRule from(RuleDefinition definition) {
        RuleArguments arguments = definition.arguments();
        return new VictimsRule(definition.name(), arguments.metric(RESCUED),
                arguments.numbers().number(POINTS_PER_RESCUED));
    }

    @Override
    public RuleDefinition definition() {
        return new RuleDefinition(TYPE, ruleName,
                RuleArguments.of(Parameters.none().with(POINTS_PER_RESCUED, pointsPerRescued))
                        .withMetric(RESCUED, rescued));
    }

    @Override
    public ResultSource source() {
        return rescued.source();
    }

    @Override
    public Set<Metric> metrics() {
        return Set.of(rescued);
    }

    @Override
    public String getRuleName() {
        return ruleName;
    }

    @Override
    public RuleEvaluation evaluate(RawMetrics metrics) {
        int saved = metrics.count(rescued);
        ScoreItem item = ScoreItem.of(
                ruleName,
                String.format(Locale.US, "%d rescatadas", saved),
                String.format(Locale.US, "%d * %.1f pts", saved, pointsPerRescued),
                saved * pointsPerRescued
        );
        return RuleEvaluation.of(item, String.format(Locale.US, "%s: %d rescatadas", ruleName, saved));
    }
}
