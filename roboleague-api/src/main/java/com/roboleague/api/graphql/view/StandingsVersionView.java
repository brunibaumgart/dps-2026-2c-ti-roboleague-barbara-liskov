package com.roboleague.api.graphql.view;

import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsVersion;

import java.util.List;

/**
 * GraphQL view of one standings version with its rows and the rulebook version that ranked it.
 */
public record StandingsVersionView(int number, String calculatedAt, String status, PublicationView publication,
                                   int rulebookVersion, List<StandingsEntryView> entries) {

    public static StandingsVersionView from(Standings standings, StandingsVersion version) {
        StandingsVersionSummaryView summary = StandingsVersionSummaryView.from(standings, version);
        return new StandingsVersionView(summary.number(), summary.calculatedAt(), summary.status(),
                summary.publication(), version.table().rulebookVersion().number(),
                version.table().entries().stream().map(StandingsEntryView::from).toList());
    }
}
