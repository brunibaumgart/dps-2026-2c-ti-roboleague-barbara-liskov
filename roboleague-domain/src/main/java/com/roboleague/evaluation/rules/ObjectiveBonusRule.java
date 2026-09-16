package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreItem;

import java.util.Objects;

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

        String formula = String.format("%d obj * %.1f pts", completed, config.pointsPerObjective());
        if (allDone) {
            formula += String.format(" + %.1f pts (bonificación total)", config.allCompletedBonus());
        }

        ScoreItem item = ScoreItem.of(
                ruleName,
                String.format("%d / %d objetivos", completed, config.totalPossibleObjectives()),
                formula,
                totalSubtotal
        );

        String note = String.format("Objetivos completados: %d/%d", completed, config.totalPossibleObjectives());
        return RuleEvaluation.of(item, note);
    }
}
