package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.RuleArguments;
import com.roboleague.evaluation.definition.RuleDefinition;
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

    public static final String TYPE = "victims";
    private static final String TOTAL_VICTIMS = "totalVictims";
    private static final String POINTS_PER_RESCUED = "pointsPerRescued";
    private static final String DEDUCTION_PER_ABANDONED = "deductionPerAbandoned";
    private static final String RESCUED = "rescued";

    public static VictimsRule from(RuleDefinition definition) {
        RuleArguments arguments = definition.arguments();
        Parameters numbers = arguments.numbers();
        return new VictimsRule(definition.name(), arguments.metric(RESCUED), new VictimTariff(
                numbers.whole(TOTAL_VICTIMS), numbers.number(POINTS_PER_RESCUED), numbers.number(DEDUCTION_PER_ABANDONED)));
    }

    @Override
    public RuleDefinition definition() {
        return new RuleDefinition(TYPE, ruleName, RuleArguments.of(Parameters.none()
                        .with(TOTAL_VICTIMS, tariff.totalVictims())
                        .with(POINTS_PER_RESCUED, tariff.pointsPerRescued())
                        .with(DEDUCTION_PER_ABANDONED, tariff.deductionPerAbandoned()))
                .withMetric(RESCUED, rescued));
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
