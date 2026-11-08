package com.roboleague.api.graphql.view;

import com.roboleague.evaluation.ScoreItem;

import java.util.List;

/**
 * GraphQL view of one contribution to a score: what it is, the metric it read, the formula and what it adds.
 */
public record ScoreItemView(String concept, String rawMetric, String formula, double subtotal) {

    public static ScoreItemView from(ScoreItem item) {
        return new ScoreItemView(item.concept(), item.rawMetric(), item.appliedFormula(), item.subtotal().doubleValue());
    }

    static List<ScoreItemView> from(List<ScoreItem> items) {
        return items.stream().map(ScoreItemView::from).toList();
    }
}
