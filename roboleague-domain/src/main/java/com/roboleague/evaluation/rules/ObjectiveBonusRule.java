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
 * Scoring rule granting bonuses for completing predefined objectives and milestones.
 */
public class ObjectiveBonusRule implements ScoreRule {
    private final String ruleName;
    private final ObjectiveRuleConfig config;

    public ObjectiveBonusRule(String ruleName, ObjectiveRuleConfig config) {
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public static ObjectiveBonusRule of(String ruleName, ObjectiveRuleConfig config) {
        return new ObjectiveBonusRule(ruleName, config);
    }

    public static ObjectiveBonusRule of(String ruleName, double pointsPerObjective, int totalObjectives, double allCompletedBonus) {
        return new ObjectiveBonusRule(ruleName, new ObjectiveRuleConfig(pointsPerObjective, totalObjectives, allCompletedBonus));
    }

    public static ObjectiveBonusRule standard(double pointsPerObjective, int totalObjectives) {
        return new ObjectiveBonusRule(
                "Bonificación por Objetivos",
                new ObjectiveRuleConfig(pointsPerObjective, totalObjectives, 25.0)
        );
    }

    public static final String TYPE = "objectives";
    private static final String POINTS_PER_OBJECTIVE = "pointsPerObjective";
    private static final String TOTAL_OBJECTIVES = "totalObjectives";
    private static final String ALL_COMPLETED_BONUS = "allCompletedBonus";

    public static ObjectiveBonusRule from(RuleDefinition definition) {
        Parameters numbers = definition.arguments().numbers();
        return new ObjectiveBonusRule(definition.name(), new ObjectiveRuleConfig(
                numbers.number(POINTS_PER_OBJECTIVE), numbers.whole(TOTAL_OBJECTIVES),
                numbers.number(ALL_COMPLETED_BONUS)));
    }

    @Override
    public RuleDefinition definition() {
        return new RuleDefinition(TYPE, ruleName, RuleArguments.of(Parameters.none()
                .with(POINTS_PER_OBJECTIVE, config.pointsPerObjective())
                .with(TOTAL_OBJECTIVES, config.totalPossibleObjectives())
                .with(ALL_COMPLETED_BONUS, config.allCompletedBonus())));
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

    public ObjectiveRuleConfig getConfig() {
        return config;
    }

    @Override
    public RuleEvaluation evaluate(RawMetrics metrics) {
        int completed = metrics.objectivesCompleted();
        double baseBonus = completed * config.pointsPerObjective();
        boolean allDone = (config.totalPossibleObjectives() > 0 && completed >= config.totalPossibleObjectives());
        double totalSubtotal = baseBonus + (allDone ? config.allCompletedBonus() : 0.0);

        String formula = String.format(Locale.US, "%d obj * %.1f pts", completed, config.pointsPerObjective());
        if (allDone) {
            formula += String.format(Locale.US, " + %.1f pts (bonificación total)", config.allCompletedBonus());
        }

        ScoreItem item = ScoreItem.of(
                ruleName,
                String.format(Locale.US, "%d / %d objetivos", completed, config.totalPossibleObjectives()),
                formula,
                totalSubtotal
        );

        String note = String.format(Locale.US, "Objetivos completados: %d/%d", completed, config.totalPossibleObjectives());
        return RuleEvaluation.of(item, note);
    }
}
