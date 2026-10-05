package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.ScoreItem;

import java.util.Locale;
import java.util.Objects;

/**
 * Deducts for each counted fault (line exits, collisions…) beyond a free allowance.
 * Line exits and collisions are two instances reading different metrics, not two classes.
 */
public final class CountedFaultRule implements ScoreRule {
    private final String ruleName;
    private final Metric faults;
    private final FaultTariff tariff;

    public CountedFaultRule(String ruleName, Metric faults, FaultTariff tariff) {
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.faults = Objects.requireNonNull(faults, "faults cannot be null");
        this.tariff = Objects.requireNonNull(tariff, "tariff cannot be null");
    }

    @Override
    public ResultSource source() {
        return faults.source();
    }

    @Override
    public String getRuleName() {
        return ruleName;
    }

    @Override
    public RuleEvaluation evaluate(RawMetrics metrics) {
        int counted = metrics.count(faults);
        int charged = Math.max(0, counted - tariff.freeAllowance());
        double subtotal = -(charged * tariff.deductionPerFault());

        ScoreItem item = ScoreItem.of(
                ruleName,
                String.format(Locale.US, "%d %s", counted, faults.name()),
                String.format(Locale.US, "max(0, %d - %d sin cargo) * -%.1f pts",
                        counted, tariff.freeAllowance(), tariff.deductionPerFault()),
                subtotal
        );
        String note = charged > 0
                ? String.format(Locale.US, "%s: %d con descuento (-%.1f pts)", ruleName, charged, Math.abs(subtotal))
                : String.format(Locale.US, "%s: dentro de lo tolerado", ruleName);
        return RuleEvaluation.of(item, note);
    }
}
