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
 * Deducts for each unit of resource or energy consumed beyond the allowed maximum.
 */
public class ResourceConsumptionRule implements DeductionRule {
    private final String ruleName;
    private final double maxAllowedConsumption;
    private final double penaltyPerExcessUnit;

    public ResourceConsumptionRule(String ruleName, double maxAllowedConsumption, double penaltyPerExcessUnit) {
        if (maxAllowedConsumption < 0) {
            throw new IllegalArgumentException("maxAllowedConsumption cannot be negative");
        }
        if (penaltyPerExcessUnit < 0) {
            throw new IllegalArgumentException("penaltyPerExcessUnit cannot be negative: excess consumption never adds points");
        }
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.maxAllowedConsumption = maxAllowedConsumption;
        this.penaltyPerExcessUnit = penaltyPerExcessUnit;
    }

    public static final String TYPE = "resource-consumption";
    private static final String MAX_ALLOWED_CONSUMPTION = "maxAllowedConsumption";
    private static final String PENALTY_PER_EXCESS_UNIT = "penaltyPerExcessUnit";

    public static ResourceConsumptionRule from(RuleDefinition definition) {
        Parameters numbers = definition.arguments().numbers();
        return new ResourceConsumptionRule(definition.name(),
                numbers.number(MAX_ALLOWED_CONSUMPTION), numbers.number(PENALTY_PER_EXCESS_UNIT));
    }

    @Override
    public RuleDefinition definition() {
        return new RuleDefinition(TYPE, ruleName, RuleArguments.of(Parameters.none()
                .with(MAX_ALLOWED_CONSUMPTION, maxAllowedConsumption)
                .with(PENALTY_PER_EXCESS_UNIT, penaltyPerExcessUnit)));
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
