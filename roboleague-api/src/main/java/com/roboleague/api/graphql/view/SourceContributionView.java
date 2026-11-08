package com.roboleague.api.graphql.view;

import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.SourceContribution;

import java.util.List;

/**
 * GraphQL view of what one result source contributed to a score (F3: measurements and judges apart).
 */
public record SourceContributionView(ResultSource source, String label, double subtotal, List<ScoreItemView> items) {

    public static SourceContributionView from(SourceContribution contribution) {
        return new SourceContributionView(contribution.source(), contribution.source().label(),
                contribution.subtotal().doubleValue(), ScoreItemView.from(contribution.items()));
    }
}
