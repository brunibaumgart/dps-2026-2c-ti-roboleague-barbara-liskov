package com.roboleague.api.graphql.view;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.Points;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.ScoreBreakdown;
import com.roboleague.evaluation.SourceDelivery;
import com.roboleague.usecase.AttemptBreakdown;

import java.util.List;
import java.util.Optional;

/**
 * GraphQL view of an attempt. {@code score} is null while it awaits a source or once it is disqualified.
 * {@code team}, {@code round}, {@code breakdown} and {@code appeals} are resolved on demand. {@code loadedBreakdown}
 * is filled when the parent already loaded it ({@code Query.attempt}, the {@code attempt} batches), so
 * {@code Attempt.breakdown} answers without another read; it is empty otherwise ({@code Turn.attempts},
 * {@code Team.results}) and the breakdown batch loads it.
 */
public record AttemptView(String id, int number, String slotId, String roundId, String teamId, String challengeId,
                          int rulebookVersion, String status, List<ResultSource> received, Double score,
                          Optional<ScoreBreakdownView> loadedBreakdown) {

    public static AttemptView from(Attempt attempt) {
        return of(attempt, Optional.empty());
    }

    public static AttemptView from(AttemptBreakdown breakdown) {
        return of(breakdown.attempt(), Optional.of(ScoreBreakdownView.from(breakdown)));
    }

    private static AttemptView of(Attempt attempt, Optional<ScoreBreakdownView> breakdown) {
        return new AttemptView(attempt.getId().value(), attempt.getId().number(), attempt.getSlotId().value(),
                attempt.getRoundId().value(), attempt.getTeamId().value(),
                attempt.getRulebookReference().challengeId().value(),
                attempt.getRulebookReference().version().number(), attempt.getStatus().name(),
                attempt.getDeliveries().stream().map(SourceDelivery::source).toList(),
                attempt.countableScore().map(ScoreBreakdown::totalScore).map(Points::doubleValue).orElse(null),
                breakdown);
    }
}
