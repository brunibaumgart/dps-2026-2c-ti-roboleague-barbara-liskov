package com.roboleague.api.graphql.view;

import com.roboleague.port.in.TurnResults;

import java.util.List;

/**
 * GraphQL view of the capture state of a turn: the rulebook version its category plays with, how many attempts that
 * version admits and which counts, whether it still takes a result, and its attempts in the order they were opened
 * (D12: one batch for the four fields).
 */
public record TurnView(int rulebookVersion, String attemptRule, boolean acceptsResult, List<AttemptView> attempts) {

    public static TurnView from(TurnResults turn) {
        return new TurnView(turn.rulebook().version().number(),
                turn.rulebook().rankingScheme().attemptSelection().describe(), turn.acceptsResult(),
                turn.attempts().stream().map(AttemptView::from).toList());
    }
}
