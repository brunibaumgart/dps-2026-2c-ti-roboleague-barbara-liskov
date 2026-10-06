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
 * Deducts for each victim of the track that was not rescued. It reads the rescued count and knows how many
 * victims there were, so it is the one that rejects more rescued victims than placed.
 */
public final class AbandonedVictimsRule implements ScoreRule {
    private final String ruleName;
    private final Metric rescued;
    private final VictimTariff tariff;

    public AbandonedVictimsRule(String ruleName, Metric rescued, VictimTariff tariff) {
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.rescued = Objects.requireNonNull(rescued, "rescued cannot be null");
        this.tariff = Objects.requireNonNull(tariff, "tariff cannot be null");
    }

    public static final String TYPE = "abandoned-victims";
    private static final String TOTAL_VICTIMS = "totalVictims";
    private static final String DEDUCTION_PER_ABANDONED = "deductionPerAbandoned";
    private static final String RESCUED = "rescued";

    public static AbandonedVictimsRule from(RuleDefinition definition) {
        RuleArguments arguments = definition.arguments();
        Parameters numbers = arguments.numbers();
        return new AbandonedVictimsRule(definition.name(), arguments.metric(RESCUED),
                new VictimTariff(numbers.whole(TOTAL_VICTIMS), numbers.number(DEDUCTION_PER_ABANDONED)));
    }

    @Override
    public RuleDefinition definition() {
        return new RuleDefinition(TYPE, ruleName, RuleArguments.of(Parameters.none()
                        .with(TOTAL_VICTIMS, tariff.totalVictims())
                        .with(DEDUCTION_PER_ABANDONED, tariff.deductionPerAbandoned()))
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
        if (saved > tariff.totalVictims()) {
            throw new IllegalArgumentException("rescued victims cannot exceed " + tariff.totalVictims() + ": " + saved);
        }
        int abandoned = tariff.totalVictims() - saved;
        ScoreItem item = ScoreItem.of(
                ruleName,
                String.format(Locale.US, "%d / %d abandonadas", abandoned, tariff.totalVictims()),
                String.format(Locale.US, "%d * -%.1f pts", abandoned, tariff.deductionPerAbandoned()),
                -(abandoned * tariff.deductionPerAbandoned())
        );
        return RuleEvaluation.of(item, String.format(Locale.US, "%s: %d abandonadas", ruleName, abandoned));
    }
}
