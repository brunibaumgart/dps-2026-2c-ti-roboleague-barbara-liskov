package com.roboleague.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ScoreBreakdown(
        List<ScoreItem> items,
        List<String> notesAndPenalties,
        double totalScore
) {
    public ScoreBreakdown {
        items = items != null ? Collections.unmodifiableList(new ArrayList<>(items)) : Collections.emptyList();
        notesAndPenalties = notesAndPenalties != null ? Collections.unmodifiableList(new ArrayList<>(notesAndPenalties)) : Collections.emptyList();
    }

    public static ScoreBreakdown of(List<ScoreItem> items, List<String> notesAndPenalties) {
        double total = items.stream().mapToDouble(ScoreItem::subtotal).sum();
        return new ScoreBreakdown(items, notesAndPenalties, Math.max(0.0, total));
    }

    public static ScoreBreakdown empty() {
        return new ScoreBreakdown(Collections.emptyList(), Collections.emptyList(), 0.0);
    }
}
