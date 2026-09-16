package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreItem;

import java.util.Locale;

/**
 * Scoring rule calculating penalties or efficiency bonuses based on resource/energy consumption.
 */
public class ResourceConsumptionRule implements ScoreRule {
    private final String ruleName;
    private final double maxAllowedConsumption;
    private final double penaltyPerExcessUnit;

    public ResourceConsumptionRule(String ruleName, double maxAllowedConsumption, double penaltyPerExcessUnit) {
        this.ruleName = ruleName;
        this.maxAllowedConsumption = maxAllowedConsumption;
        this.penaltyPerExcessUnit = penaltyPerExcessUnit;
    }

    @Override
    public String getRuleName() {
        return ruleName;
    }

    @Override
    public RuleEvaluation evaluate(RawMetrics metrics) {
        double consumed = metrics.resourceConsumption();
        double excess = Math.max(0.0, consumed - maxAllowedConsumption);
        double subtotal = -(excess * penaltyPerExcessUnit);

        String rawMetric = String.format(Locale.US, "%.2f unidades (máx: %.2f)", consumed, maxAllowedConsumption);
        String formula = excess > 0
                ? String.format(Locale.US, "Exceso %.2f * -%.2f pts", excess, penaltyPerExcessUnit)
                : "Dentro del límite permitido";

        ScoreItem item = ScoreItem.of(ruleName, rawMetric, formula, subtotal);
        String note = excess > 0
                ? String.format(Locale.US, "Consumo excesivo: %.2f unidades sobre el límite", excess)
                : "Consumo de recursos eficiente";

        return RuleEvaluation.of(item, note);
    }
}
