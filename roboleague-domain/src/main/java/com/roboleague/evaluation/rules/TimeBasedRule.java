package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.RuleArguments;
import com.roboleague.evaluation.definition.RuleDefinition;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.ScoreItem;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Scoring rule calculating points according to execution time.
 */
public class TimeBasedRule implements BaseRule {
    private final String ruleName;
    private final TimeRuleConfig config;

    public TimeBasedRule(String ruleName, TimeRuleConfig config) {
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public static TimeBasedRule of(String ruleName, TimeRuleConfig config) {
        return new TimeBasedRule(ruleName, config);
    }

    public static TimeBasedRule of(String ruleName, Points basePoints, double targetTimeSeconds,
                                   double pointsPerSecondUnder, double deductionPerSecondOver, Points minPoints) {
        return new TimeBasedRule(
                ruleName,
                TimeRuleConfig.of(basePoints, targetTimeSeconds, pointsPerSecondUnder, deductionPerSecondOver, minPoints)
        );
    }

    public static TimeBasedRule standard(Points basePoints, double targetTimeSeconds) {
        return new TimeBasedRule(
                "Regla de Tiempo",
                TimeRuleConfig.of(basePoints, targetTimeSeconds, 1.5, 2.0, Points.ZERO)
        );
    }

    public static final String TYPE = "time";
    private static final String BASE_POINTS = "basePoints";
    private static final String TARGET_TIME_SECONDS = "targetTimeSeconds";
    private static final String POINTS_PER_SECOND_UNDER = "pointsPerSecondUnder";
    private static final String DEDUCTION_PER_SECOND_OVER = "deductionPerSecondOver";
    private static final String MIN_POINTS = "minPoints";

    public static TimeBasedRule from(RuleDefinition definition) {
        Parameters numbers = definition.arguments().numbers();
        return new TimeBasedRule(definition.name(), TimeRuleConfig.of(
                numbers.points(BASE_POINTS), numbers.number(TARGET_TIME_SECONDS),
                numbers.number(POINTS_PER_SECOND_UNDER), numbers.number(DEDUCTION_PER_SECOND_OVER),
                numbers.points(MIN_POINTS)));
    }

    @Override
    public RuleDefinition definition() {
        return new RuleDefinition(TYPE, ruleName, RuleArguments.of(Parameters.none()
                .with(BASE_POINTS, config.targets().basePoints())
                .with(TARGET_TIME_SECONDS, config.targets().targetTimeSeconds())
                .with(POINTS_PER_SECOND_UNDER, config.adjustments().pointsPerSecondUnder())
                .with(DEDUCTION_PER_SECOND_OVER, config.adjustments().deductionPerSecondOver())
                .with(MIN_POINTS, config.adjustments().minPoints())));
    }
    @Override
    public ResultSource source() {
        return ResultSource.AUTOMATIC_MEASUREMENTS;
    }

    @Override
    public Set<Metric> metrics() {
        return Set.of();
    }

    @Override
    public String getRuleName() {
        return ruleName;
    }

    public TimeRuleConfig getConfig() {
        return config;
    }

    @Override
    public RuleEvaluation evaluate(RawMetrics metrics) {
        double time = metrics.timeTakenSeconds();
        Points basePoints = config.targets().basePoints();
        double targetTimeSeconds = config.targets().targetTimeSeconds();
        BigDecimal delta = BigDecimal.valueOf(targetTimeSeconds).subtract(BigDecimal.valueOf(time));
        Points subtotal;
        String formula;

        if (delta.signum() >= 0) {
            BigDecimal rate = BigDecimal.valueOf(config.adjustments().pointsPerSecondUnder());
            Points bonus = Points.of(delta.multiply(rate));
            subtotal = basePoints.plus(bonus);
            formula = String.format(Locale.US, "Base %.1f + (%.2fs por debajo del objetivo * %.2f)",
                    basePoints.toBigDecimal(), delta, config.adjustments().pointsPerSecondUnder());
        } else {
            BigDecimal over = delta.negate();
            BigDecimal rate = BigDecimal.valueOf(config.adjustments().deductionPerSecondOver());
            Points penalty = Points.of(over.multiply(rate));
            subtotal = config.adjustments().minPoints().max(basePoints.minus(penalty));
            formula = String.format(Locale.US, "Base %.1f - (%.2fs por encima del objetivo * %.2f)",
                    basePoints.toBigDecimal(), over, config.adjustments().deductionPerSecondOver());
        }

        ScoreItem item = ScoreItem.of(
                ruleName,
                String.format(Locale.US, "%.2f s", time),
                formula,
                subtotal
        );

        String note = String.format(Locale.US, "Tiempo registrado: %.2fs (tiempo objetivo: %.2fs)", time, targetTimeSeconds);
        return RuleEvaluation.of(item, note);
    }
}
