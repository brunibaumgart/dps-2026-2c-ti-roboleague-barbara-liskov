package com.roboleague.api.graphql.view;

import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsId;
import com.roboleague.usecase.StandingsOverview;

import java.util.List;

/**
 * GraphQL view of the standings of a challenge in a category: every version without its rows, the latest and the
 * official one with them, and what would block publishing now. Before the first calculation there are no versions
 * and both {@code latest} and {@code official} are null.
 */
public record StandingsView(String challengeId, String categoryId, List<StandingsVersionSummaryView> versions,
                            StandingsVersionView latest, StandingsVersionView official, PendingView pending) {

    public static StandingsView from(StandingsId id, StandingsOverview overview) {
        PendingView pending = PendingView.from(overview);
        String challengeId = id.challengeId().value();
        String categoryId = id.categoryId().value();
        return overview.standings()
                .map(standings -> new StandingsView(challengeId, categoryId,
                        standings.versions().stream()
                                .map(version -> StandingsVersionSummaryView.from(standings, version)).toList(),
                        StandingsVersionView.from(standings, standings.latest()), official(standings), pending))
                .orElseGet(() -> new StandingsView(challengeId, categoryId, List.of(), null, null, pending));
    }

    private static StandingsVersionView official(Standings standings) {
        return standings.official().map(version -> StandingsVersionView.from(standings, version)).orElse(null);
    }
}
