package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.ScoreItem;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Adds points for each rescued victim and deducts for each one abandoned, as two separate items
 * so the reward and the deduction can be told apart in the breakdown.
 */
public final class VictimsRule implements ScoreRule {
    private final String ruleName;
    private final Metric rescued;
    private final VictimTariff tariff;

    public VictimsRule(String ruleName, Metric rescued, VictimTariff tariff) {
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.rescued = Objects.requireNonNull(rescued, "rescued cannot be null");
        this.tariff = Objects.requireNonNull(tariff, "tariff cannot be null");
    }

    @Override
    public ResultSource source() {
        return rescued.source();
    }

    @Override
    public String getRuleName() {
        return ruleName;
    }

    @Override
    public RuleEvaluation evaluate(RawMetrics metrics) {
        int saved = metrics.count(rescued);
        if (saved > tariff.totalVictims()) {
            throw new IllegalArgumentException("rescued victims cannot exceed " + tariff.totalVictims() + ": " + saved);
        }
        int abandoned = tariff.totalVictims() - saved;

        ScoreItem rescuedItem = ScoreItem.of(
                ruleName + ": rescatadas",
                String.format(Locale.US, "%d / %d", saved, tariff.totalVictims()),
                String.format(Locale.US, "%d * %.1f pts", saved, tariff.pointsPerRescued()),
                saved * tariff.pointsPerRescued()
        );
        ScoreItem abandonedItem = ScoreItem.of(
                ruleName + ": abandonadas",
                String.format(Locale.US, "%d / %d", abandoned, tariff.totalVictims()),
                String.format(Locale.US, "%d * -%.1f pts", abandoned, tariff.deductionPerAbandoned()),
                -(abandoned * tariff.deductionPerAbandoned())
        );
        String note = String.format(Locale.US, "%s: %d rescatadas, %d abandonadas", ruleName, saved, abandoned);
        return new RuleEvaluation(List.of(rescuedItem, abandonedItem), List.of(note));
    }
}
