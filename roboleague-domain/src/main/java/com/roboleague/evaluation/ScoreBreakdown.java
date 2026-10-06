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
        double sum = sumOfSections();
        if (sum < 0) {
            items.add(ScoreItem.of(FLOOR_CONCEPT, String.format(Locale.US, "suma %.2f", sum), "max(0, suma)", -sum));
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
    public double deducted() {
        return Math.abs(deductions.total());
    }

    public double totalScore() {
        return Math.max(0.0, sumOfSections());
    }

    private double sumOfSections() {
        return base.total() + bonuses.total() + deductions.total();
    }
}
