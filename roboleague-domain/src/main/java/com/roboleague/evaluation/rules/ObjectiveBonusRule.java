package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreItem;

/**
 * Scoring rule granting bonuses for completing predefined objectives and milestones.
 */
public class ObjectiveBonusRule implements ScoreRule {
    private final String ruleName;
    private final double pointsPerObjective;
    private final int totalPossibleObjectives;
    private final double allCompletedBonus;

    public ObjectiveBonusRule(String ruleName, double pointsPerObjective, int totalPossibleObjectives, double allCompletedBonus) {
        this.ruleName = ruleName;
        this.pointsPerObjective = pointsPerObjective;
        this.totalPossibleObjectives = totalPossibleObjectives;
        this.allCompletedBonus = allCompletedBonus;
    }

    public static ObjectiveBonusRule standard(double pointsPerObjective, int totalObjectives) {
        return new ObjectiveBonusRule("Bonificación por Objetivos", pointsPerObjective, totalObjectives, 25.0);
    }

    @Override
    public String getRuleName() {
        return ruleName;
    }

    @Override
    public RuleEvaluation evaluate(RawMetrics metrics) {
        int completed = metrics.objectivesCompleted();
        double baseBonus = completed * pointsPerObjective;
        boolean allDone = (totalPossibleObjectives > 0 && completed >= totalPossibleObjectives);
        double totalSubtotal = baseBonus + (allDone ? allCompletedBonus : 0.0);

        String formula = String.format("%d obj * %.1f pts", completed, pointsPerObjective);
        if (allDone) {
            formula += String.format(" + %.1f pts (bonificación total)", allCompletedBonus);
        }

        ScoreItem item = new ScoreItem(
                ruleName,
                String.format("%d / %d objetivos", completed, totalPossibleObjectives),
                formula,
                totalSubtotal
        );

        String note = String.format("Objetivos completados: %d/%d", completed, totalPossibleObjectives);
        return RuleEvaluation.of(item, note);
    }
}
