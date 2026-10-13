package com.roboleague.evaluation.rules;

import com.roboleague.evaluation.definition.Parameters;
import com.roboleague.evaluation.definition.RuleArguments;
import com.roboleague.evaluation.definition.RuleDefinition;
import com.roboleague.evaluation.Metric;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.ScoreItem;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Scoring rule applying deductions for fouls or track infractions.
 */
public class PenaltyRule implements DeductionRule {
    private final String ruleName;
    private final Points deductionPerPenalty;

    public PenaltyRule(String ruleName, Points deductionPerPenalty) {
        if (deductionPerPenalty.isNegative()) {
            throw new IllegalArgumentException("deductionPerPenalty cannot be negative: a penalty never adds points");
        }
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.deductionPerPenalty = deductionPerPenalty;
    }

    public static final String TYPE = "penalty";
    private static final String DEDUCTION_PER_PENALTY = "deductionPerPenalty";

    public static PenaltyRule from(RuleDefinition definition) {
        return new PenaltyRule(definition.name(), definition.arguments().numbers().points(DEDUCTION_PER_PENALTY));
    }

    @Override
    public RuleDefinition definition() {
        return new RuleDefinition(TYPE, ruleName,
                RuleArguments.of(Parameters.none().with(DEDUCTION_PER_PENALTY, deductionPerPenalty)));
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
        int penalties = metrics.penaltiesCount();
        Points subtotal = deductionPerPenalty.times(penalties).negate();
        String formula = String.format(Locale.US, "%d faltas * -%.1f pts", penalties,
                deductionPerPenalty.toBigDecimal());

        ScoreItem item = ScoreItem.of(
                ruleName,
                String.format(Locale.US, "%d faltas", penalties),
                formula,
                subtotal
        );

        String note = penalties > 0
                ? String.format(Locale.US, "Penalizaciones aplicadas: %d faltas (-%.1f pts)", penalties,
                        subtotal.abs().toBigDecimal())
                : "Sin penalizaciones en pista";

        return RuleEvaluation.of(item, note);
    }
}
