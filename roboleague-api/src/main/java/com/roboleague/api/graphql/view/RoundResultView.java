package com.roboleague.api.graphql.view;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.ranking.RoundResult;

import java.util.List;

/**
 * GraphQL view of a round of a team in the standings: the attempt that counted, its total and the other attempts of
 * the turn. {@code round} and {@code attempt} are resolved in batch from the ids kept here.
 */
public record RoundResultView(String roundId, String attemptId, double total, List<String> otherAttemptIds) {

    public static RoundResultView from(RoundResult round) {
        return new RoundResultView(round.roundId().value(), round.attemptId().value(), round.total().doubleValue(),
                round.otherAttempts().stream().map(AttemptId::value).toList());
    }
}
