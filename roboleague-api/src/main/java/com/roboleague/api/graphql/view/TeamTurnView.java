package com.roboleague.api.graphql.view;

import com.roboleague.usecase.TeamTurn;

import java.util.List;

/**
 * GraphQL view of one turn of a team: its round, its slot and the attempts it ran there. Attempts are built without
 * their breakdown; {@code Attempt.breakdown} loads it in batch only if the query asks for it.
 */
public record TeamTurnView(RoundView round, SlotView slot, List<AttemptView> attempts) {

    public static TeamTurnView from(TeamTurn turn) {
        return new TeamTurnView(RoundView.from(turn.round()), SlotView.from(turn.round(), turn.slot()),
                turn.attempts().stream().map(AttemptView::from).toList());
    }
}
