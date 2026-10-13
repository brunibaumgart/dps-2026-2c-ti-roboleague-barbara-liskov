package com.roboleague.evaluation;

import com.roboleague.evaluation.rules.ScoreRule.RuleEvaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * How an attempt scored, by section: what its base rules gave, its bonuses with the cap applied to them, and what
 * its deductions took away. The total is the sum of every item, never below zero: when the sum is negative, a
 * "Piso en cero" item shows what the floor added, so the items always explain the total.
 */
public record ScoreBreakdown(RuleEvaluation base, RuleEvaluation bonuses, RuleEvaluation deductions) {
    private static final String FLOOR_CONCEPT = "Piso en cero";

    public ScoreBreakdown {
        Objects.requireNonNull(base, "base cannot be null");
        Objects.requireNonNull(bonuses, "bonuses cannot be null");
        Objects.requireNonNull(deductions, "deductions cannot be null");
    }

    public static ScoreBreakdown empty() {
        return new ScoreBreakdown(RuleEvaluation.empty(), RuleEvaluation.empty(), RuleEvaluation.empty());
    }

    public List<ScoreItem> items() {
        List<ScoreItem> items = new ArrayList<>(base.items());
        items.addAll(bonuses.items());
        items.addAll(deductions.items());
        Points sum = sumOfSections();
        if (sum.isNegative()) {
            items.add(ScoreItem.of(FLOOR_CONCEPT, String.format(Locale.US, "suma %.2f", sum.toBigDecimal()),
                    "max(0, suma)", sum.negate()));
        }
        return List.copyOf(items);
    }

    public List<String> notesAndPenalties() {
        List<String> notes = new ArrayList<>(base.notes());
        notes.addAll(bonuses.notes());
        notes.addAll(deductions.notes());
        return List.copyOf(notes);
    }

    /**
     * Points the deductions took away, as a positive amount.
     */
    public Points deducted() {
        return deductions.total().abs();
    }

    public Points totalScore() {
        return sumOfSections().max(Points.ZERO);
    }

    private Points sumOfSections() {
        return base.total().plus(bonuses.total()).plus(deductions.total());
    }
}
