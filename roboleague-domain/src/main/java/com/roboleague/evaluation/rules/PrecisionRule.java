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
 * Awards a share of the maximum points proportional to a measured precision between 0 and 1.
 */
public final class PrecisionRule implements BaseRule {
    private final String ruleName;
    private final Metric accuracy;
    private final double maxPoints;

    public PrecisionRule(String ruleName, Metric accuracy, double maxPoints) {
        if (maxPoints < 0) {
            throw new IllegalArgumentException("maxPoints cannot be negative");
        }
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.accuracy = Objects.requireNonNull(accuracy, "accuracy cannot be null");
        this.maxPoints = maxPoints;
    }

    public static final String TYPE = "precision";
    private static final String MAX_POINTS = "maxPoints";
    private static final String ACCURACY = "accuracy";

    public static PrecisionRule from(RuleDefinition definition) {
        RuleArguments arguments = definition.arguments();
        return new PrecisionRule(definition.name(), arguments.metric(ACCURACY), arguments.numbers().number(MAX_POINTS));
    }

    @Override
    public RuleDefinition definition() {
        return new RuleDefinition(TYPE, ruleName,
                RuleArguments.of(Parameters.none().with(MAX_POINTS, maxPoints)).withMetric(ACCURACY, accuracy));
    }
    @Override
    public ResultSource source() {
        return accuracy.source();
    }

    @Override
    public Set<Metric> metrics() {
        return Set.of(accuracy);
    }

    @Override
    public String getRuleName() {
        return ruleName;
    }

    @Override
    public RuleEvaluation evaluate(RawMetrics metrics) {
        double precision = metrics.measurement(accuracy);
        if (precision < 0 || precision > 1) {
            throw new IllegalArgumentException("precision must be between 0 and 1: " + precision);
        }
        double subtotal = maxPoints * precision;

        ScoreItem item = ScoreItem.of(
                ruleName,
                String.format(Locale.US, "%.0f%% de precisión", precision * 100),
                String.format(Locale.US, "%.1f pts * %.2f", maxPoints, precision),
                subtotal
        );
        return RuleEvaluation.of(item, String.format(Locale.US, "%s: %.1f de %.1f pts", ruleName, subtotal, maxPoints));
    }
}
