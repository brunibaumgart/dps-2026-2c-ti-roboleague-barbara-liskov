package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreItem;

import java.util.Locale;

/**
 * Scoring rule incorporating subjective evaluations by human judges (e.g. design, innovation, robustness).
 */
public class JudgeSubjectiveRule implements ScoreRule {
    private final String ruleName;
    private final double weightMultiplier;

    public JudgeSubjectiveRule(String ruleName, double weightMultiplier) {
        this.ruleName = ruleName;
        this.weightMultiplier = weightMultiplier;
    }

    public static JudgeSubjectiveRule standard(double weightMultiplier) {
        return new JudgeSubjectiveRule("Evaluación de Jueces", weightMultiplier);
    }

    @Override
    public String getRuleName() {
        return ruleName;
    }

    @Override
    public RuleEvaluation evaluate(RawMetrics metrics) {
        double avg = metrics.getAverageJudgeScore();
        double subtotal = avg * weightMultiplier;
        int judgeCount = metrics.judgeSubjectiveScores().size();

        String rawMetric = String.format(Locale.US, "Promedio %.2f (%d jueces)", avg, judgeCount);
        String formula = String.format(Locale.US, "%.2f * %.2f peso", avg, weightMultiplier);

        ScoreItem item = new ScoreItem(
                ruleName,
                rawMetric,
                formula,
                subtotal
        );

        String note = String.format(Locale.US, "Puntuación subjetiva de jueces: %.2f pts", subtotal);
        return RuleEvaluation.of(item, note);
    }
}
