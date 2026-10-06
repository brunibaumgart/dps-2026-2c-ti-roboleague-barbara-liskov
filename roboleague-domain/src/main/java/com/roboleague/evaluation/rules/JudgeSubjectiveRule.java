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
 * Scoring rule incorporating subjective evaluations by human judges (e.g. design, innovation, robustness).
 */
public class JudgeSubjectiveRule implements BaseRule {
    private final String ruleName;
    private final double weightMultiplier;

    public JudgeSubjectiveRule(String ruleName, double weightMultiplier) {
        if (weightMultiplier < 0) {
            throw new IllegalArgumentException("weightMultiplier cannot be negative: a judge score never subtracts points");
        }
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.weightMultiplier = weightMultiplier;
    }

    public static JudgeSubjectiveRule standard(double weightMultiplier) {
        return new JudgeSubjectiveRule("Evaluación de Jueces", weightMultiplier);
    }

    public static final String TYPE = "judges";
    private static final String WEIGHT_MULTIPLIER = "weightMultiplier";

    public static JudgeSubjectiveRule from(RuleDefinition definition) {
        return new JudgeSubjectiveRule(definition.name(), definition.arguments().numbers().number(WEIGHT_MULTIPLIER));
    }

    @Override
    public RuleDefinition definition() {
        return new RuleDefinition(TYPE, ruleName,
                RuleArguments.of(Parameters.none().with(WEIGHT_MULTIPLIER, weightMultiplier)));
    }
    @Override
    public ResultSource source() {
        return ResultSource.JUDGE_PANEL;
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
        double avg = metrics.getAverageJudgeScore();
        double subtotal = avg * weightMultiplier;
        int judgeCount = metrics.judgeSubjectiveScores().size();

        String rawMetric = String.format(Locale.US, "Promedio %.2f (%d jueces)", avg, judgeCount);
        String formula = String.format(Locale.US, "%.2f * %.2f peso", avg, weightMultiplier);

        ScoreItem item = ScoreItem.of(
                ruleName,
                rawMetric,
                formula,
                subtotal
        );

        String note = String.format(Locale.US, "Puntuación subjetiva de jueces: %.2f pts", subtotal);
        return RuleEvaluation.of(item, note);
    }
}
