package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreItem;

import java.util.Locale;
import java.util.Objects;

/**
 * Scoring rule calculating points according to execution time.
 */
public class TimeBasedRule implements ScoreRule {
    private final String ruleName;
    private final TimeRuleConfig config;

    public TimeBasedRule(String ruleName, TimeRuleConfig config) {
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public static TimeBasedRule of(String ruleName, TimeRuleConfig config) {
        return new TimeBasedRule(ruleName, config);
    }

    public static TimeBasedRule of(String ruleName, double basePoints, double targetTimeSeconds,
                                   double pointsPerSecondUnder, double deductionPerSecondOver, double minPoints) {
        return new TimeBasedRule(
                ruleName,
                TimeRuleConfig.of(basePoints, targetTimeSeconds, pointsPerSecondUnder, deductionPerSecondOver, minPoints)
        );
    }

    public static TimeBasedRule standard(double basePoints, double targetTimeSeconds) {
        return new TimeBasedRule(
                "Regla de Tiempo",
                TimeRuleConfig.of(basePoints, targetTimeSeconds, 1.5, 2.0, 0.0)
        );
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
        double basePoints = config.targets().basePoints();
        double targetTimeSeconds = config.targets().targetTimeSeconds();
        double delta = targetTimeSeconds - time;
        double subtotal;
        String formula;

        if (delta >= 0) {
            double bonus = delta * config.adjustments().pointsPerSecondUnder();
            subtotal = basePoints + bonus;
            formula = String.format(Locale.US, "Base %.1f + (%.2fs por debajo del objetivo * %.2f)",
                    basePoints, delta, config.adjustments().pointsPerSecondUnder());
        } else {
            double penalty = Math.abs(delta) * config.adjustments().deductionPerSecondOver();
            subtotal = Math.max(config.adjustments().minPoints(), basePoints - penalty);
            formula = String.format(Locale.US, "Base %.1f - (%.2fs por encima del objetivo * %.2f)",
                    basePoints, Math.abs(delta), config.adjustments().deductionPerSecondOver());
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
