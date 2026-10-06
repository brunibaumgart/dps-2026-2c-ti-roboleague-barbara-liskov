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
 * Grants a fixed bonus when every objective of the track is completed, and nothing otherwise.
 * As a bonus rule, the rulebook's bonus cap applies to it (F2).
 */
public final class AllObjectivesBonusRule implements BonusRule {
    private final String ruleName;
    private final int totalObjectives;
    private final double bonus;

    public AllObjectivesBonusRule(String ruleName, int totalObjectives, double bonus) {
        if (totalObjectives < 1) {
            throw new IllegalArgumentException("totalObjectives must be positive");
        }
        if (bonus < 0) {
            throw new IllegalArgumentException("bonus cannot be negative");
        }
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.totalObjectives = totalObjectives;
        this.bonus = bonus;
    }

    public static final String TYPE = "all-objectives";
    private static final String TOTAL_OBJECTIVES = "totalObjectives";
    private static final String BONUS = "bonus";

    public static AllObjectivesBonusRule from(RuleDefinition definition) {
        Parameters numbers = definition.arguments().numbers();
        return new AllObjectivesBonusRule(definition.name(), numbers.whole(TOTAL_OBJECTIVES), numbers.number(BONUS));
    }

    @Override
    public RuleDefinition definition() {
        return new RuleDefinition(TYPE, ruleName, RuleArguments.of(Parameters.none()
                .with(TOTAL_OBJECTIVES, totalObjectives)
                .with(BONUS, bonus)));
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
        int completed = metrics.objectivesCompleted();
        boolean allDone = completed >= totalObjectives;
        ScoreItem item = ScoreItem.of(
                ruleName,
                String.format(Locale.US, "%d / %d objetivos", completed, totalObjectives),
                allDone
                        ? String.format(Locale.US, "todos los objetivos: +%.1f pts", bonus)
                        : "faltan objetivos: 0 pts",
                allDone ? bonus : 0.0
        );
        return RuleEvaluation.of(item, String.format(Locale.US, "%s: %d de %d objetivos",
                ruleName, completed, totalObjectives));
    }
}
