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
 * Scoring rule granting points for each objective completed. The bonus for completing all of them is a separate
 * bonus rule ({@link AllObjectivesBonusRule}), so the bonus cap sees it.
 */
public class ObjectivesRule implements BaseRule {
    private final String ruleName;
    private final double pointsPerObjective;

    public ObjectivesRule(String ruleName, double pointsPerObjective) {
        if (pointsPerObjective < 0) {
            throw new IllegalArgumentException("pointsPerObjective cannot be negative");
        }
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.pointsPerObjective = pointsPerObjective;
    }

    public static ObjectivesRule standard(double pointsPerObjective) {
        return new ObjectivesRule("Objetivos", pointsPerObjective);
    }

    public static final String TYPE = "objectives";
    private static final String POINTS_PER_OBJECTIVE = "pointsPerObjective";

    public static ObjectivesRule from(RuleDefinition definition) {
        return new ObjectivesRule(definition.name(), definition.arguments().numbers().number(POINTS_PER_OBJECTIVE));
    }

    @Override
    public RuleDefinition definition() {
        return new RuleDefinition(TYPE, ruleName,
                RuleArguments.of(Parameters.none().with(POINTS_PER_OBJECTIVE, pointsPerObjective)));
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
        ScoreItem item = ScoreItem.of(
                ruleName,
                String.format(Locale.US, "%d objetivos", completed),
                String.format(Locale.US, "%d obj * %.1f pts", completed, pointsPerObjective),
                completed * pointsPerObjective
        );
        return RuleEvaluation.of(item, String.format(Locale.US, "Objetivos completados: %d", completed));
    }
}
