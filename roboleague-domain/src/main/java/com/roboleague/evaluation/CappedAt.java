package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.ScoreRule.RuleEvaluation;

import java.util.Locale;

/**
 * Global cap on the sum of all bonuses. The breakdown always shows what was obtained, the cap and the cut.
 */
public record CappedAt(double maximum) implements BonusLimit {
    private static final String CONCEPT = "Tope de bonificaciones";

    public CappedAt {
        if (!Double.isFinite(maximum) || maximum < 0) {
            throw new IllegalArgumentException("maximum must be a finite, non-negative number: " + maximum);
        }
    }

    @Override
    public RuleEvaluation limit(double obtainedBonuses) {
        String obtained = String.format(Locale.US, "bonificaciones obtenidas %.1f pts", obtainedBonuses);
        if (obtainedBonuses <= maximum) {
            return RuleEvaluation.of(
                    ScoreItem.of(CONCEPT, obtained, String.format(Locale.US, "tope %.1f pts, sin recorte", maximum), 0.0),
                    String.format(Locale.US, "Las bonificaciones (%.1f pts) están dentro del tope de %.1f pts",
                            obtainedBonuses, maximum));
        }
        double cut = obtainedBonuses - maximum;
        return RuleEvaluation.of(
                ScoreItem.of(CONCEPT, obtained, String.format(Locale.US, "tope %.1f pts, recorte -%.1f pts", maximum, cut), -cut),
                String.format(Locale.US, "Las bonificaciones (%.1f pts) superan el tope de %.1f pts: se recortan %.1f pts",
                        obtainedBonuses, maximum, cut));
    }
}
