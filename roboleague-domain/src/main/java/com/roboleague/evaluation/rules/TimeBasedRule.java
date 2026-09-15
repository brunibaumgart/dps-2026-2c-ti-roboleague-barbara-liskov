package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreItem;

import java.util.Locale;

/**
 * Scoring rule calculating points according to execution time.
 */
public class TimeBasedRule implements ScoreRule {
    private final String ruleName;
    private final double basePoints;
    private final double targetTimeSeconds;
    private final double pointsPerSecondUnder;
    private final double deductionPerSecondOver;
    private final double minPoints;

    public TimeBasedRule(String ruleName, double basePoints, double targetTimeSeconds,
                         double pointsPerSecondUnder, double deductionPerSecondOver, double minPoints) {
        this.ruleName = ruleName;
        this.basePoints = basePoints;
        this.targetTimeSeconds = targetTimeSeconds;
        this.pointsPerSecondUnder = pointsPerSecondUnder;
        this.deductionPerSecondOver = deductionPerSecondOver;
        this.minPoints = minPoints;
    }

    public static TimeBasedRule standard(double basePoints, double targetTimeSeconds) {
        return new TimeBasedRule("Regla de Tiempo", basePoints, targetTimeSeconds, 1.5, 2.0, 0.0);
    }

    @Override
    public String getRuleName() {
        return ruleName;
    }

    @Override
    public RuleEvaluation evaluate(RawMetrics metrics) {
        double time = metrics.timeTakenSeconds();
        double delta = targetTimeSeconds - time;
        double subtotal;
        String formula;

        if (delta >= 0) {
            double bonus = delta * pointsPerSecondUnder;
            subtotal = basePoints + bonus;
            formula = String.format(Locale.US, "Base %.1f + (%.2fs por debajo del objetivo * %.2f)", basePoints, delta, pointsPerSecondUnder);
        } else {
            double penalty = Math.abs(delta) * deductionPerSecondOver;
            subtotal = Math.max(minPoints, basePoints - penalty);
            formula = String.format(Locale.US, "Base %.1f - (%.2fs por encima del objetivo * %.2f)", basePoints, Math.abs(delta), deductionPerSecondOver);
        }

        ScoreItem item = new ScoreItem(
                ruleName,
                String.format(Locale.US, "%.2f s", time),
                formula,
                subtotal
        );

        String note = String.format(Locale.US, "Tiempo registrado: %.2fs (tiempo objetivo: %.2fs)", time, targetTimeSeconds);
        return RuleEvaluation.of(item, note);
    }
}
