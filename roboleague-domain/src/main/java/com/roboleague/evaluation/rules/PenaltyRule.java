package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreItem;

import java.util.Locale;

/**
 * Scoring rule applying deductions for fouls or track infractions.
 */
public class PenaltyRule implements ScoreRule {
    private final String ruleName;
    private final double deductionPerPenalty;

    public PenaltyRule(String ruleName, double deductionPerPenalty) {
        this.ruleName = ruleName;
        this.deductionPerPenalty = deductionPerPenalty;
    }

    public static PenaltyRule standard(double deductionPerPenalty) {
        return new PenaltyRule("Penalizaciones por Faltas", deductionPerPenalty);
    }

    @Override
    public String getRuleName() {
        return ruleName;
    }

    @Override
    public RuleEvaluation evaluate(RawMetrics metrics) {
        int penalties = metrics.penaltiesCount();
        double subtotal = -(penalties * deductionPerPenalty);
        String formula = String.format(Locale.US, "%d faltas * -%.1f pts", penalties, deductionPerPenalty);

        ScoreItem item = ScoreItem.of(
                ruleName,
                String.format(Locale.US, "%d faltas", penalties),
                formula,
                subtotal
        );

        String note = penalties > 0
                ? String.format(Locale.US, "Penalizaciones aplicadas: %d faltas (-%.1f pts)", penalties, Math.abs(subtotal))
                : "Sin penalizaciones en pista";

        return RuleEvaluation.of(item, note);
    }
}
