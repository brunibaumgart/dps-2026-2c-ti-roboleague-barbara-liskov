package com.roboleague.api.graphql.view;

import com.roboleague.usecase.StandingsOverview;

/**
 * GraphQL view of what blocks publishing now: open appeals, turns without a result, and whether the latest version
 * no longer matches the results.
 */
public record PendingView(int openAppeals, int unfinishedTurns, boolean outdated) {

    public static PendingView from(StandingsOverview overview) {
        return new PendingView(overview.now().openAppeals(), overview.now().unfinishedTurns(), overview.outdated());
    }
}
