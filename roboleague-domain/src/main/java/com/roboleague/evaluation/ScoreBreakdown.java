package com.roboleague.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public record ScoreBreakdown(
        List<ScoreItem> items,
        List<String> notesAndPenalties,
        double totalScore
) {
    private static final double TOLERANCE = 0.005;
    private static final String FLOOR_CONCEPT = "Piso en cero";

    public ScoreBreakdown {
        items = items != null ? List.copyOf(items) : List.of();
        notesAndPenalties = notesAndPenalties != null ? List.copyOf(notesAndPenalties) : List.of();
        double itemsSum = sumOf(items);
        if (Math.abs(itemsSum - totalScore) > TOLERANCE) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "totalScore %.2f must be the sum of its items (%.2f)", totalScore, itemsSum));
        }
    }

    public static ScoreBreakdown of(List<ScoreItem> items, List<String> notesAndPenalties) {
        double itemsSum = sumOf(items);
        if (itemsSum >= 0) {
            return new ScoreBreakdown(items, notesAndPenalties, itemsSum);
        }
        List<ScoreItem> withFloor = new ArrayList<>(items);
        withFloor.add(ScoreItem.of(
                FLOOR_CONCEPT,
                String.format(Locale.US, "suma %.2f", itemsSum),
                "max(0, suma)",
                -itemsSum
        ));
        return new ScoreBreakdown(withFloor, notesAndPenalties, 0.0);
    }

    public static ScoreBreakdown empty() {
        return new ScoreBreakdown(Collections.emptyList(), Collections.emptyList(), 0.0);
    }

    private static double sumOf(List<ScoreItem> items) {
        double sum = 0.0;
        for (ScoreItem item : items) {
            sum += item.subtotal();
        }
        return sum;
    }
}
