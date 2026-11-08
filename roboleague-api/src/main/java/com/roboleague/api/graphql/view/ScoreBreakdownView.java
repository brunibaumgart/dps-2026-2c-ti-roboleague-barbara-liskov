package com.roboleague.api.graphql.view;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.ScoreBreakdown;
import com.roboleague.usecase.AttemptBreakdown;

import java.util.List;

/**
 * GraphQL view of how an attempt scored, the same content as the REST {@code BreakdownDto}: the sources it still
 * awaits, every item of its latest revision (bonus cap and zero floor included), what each source contributed and
 * every revision with its author and reason. Before it is scored, {@code total} is null and there are no items.
 */
public record ScoreBreakdownView(Double total, List<ResultSource> awaiting, List<ScoreItemView> items,
                                 List<String> notes, List<SourceContributionView> bySource,
                                 List<RevisionView> revisions) {

    public static ScoreBreakdownView from(AttemptBreakdown breakdown) {
        Attempt attempt = breakdown.attempt();
        ScoreBreakdown latest = attempt.getScoreBreakdown();
        return new ScoreBreakdownView(
                attempt.countableScore().map(ScoreBreakdown::totalScore).map(Points::doubleValue).orElse(null),
                breakdown.awaitedSources().stream().sorted().toList(), ScoreItemView.from(latest.items()),
                latest.notesAndPenalties(), breakdown.bySource().stream().map(SourceContributionView::from).toList(),
                attempt.getRevisionHistory().stream().map(RevisionView::from).toList());
    }
}
