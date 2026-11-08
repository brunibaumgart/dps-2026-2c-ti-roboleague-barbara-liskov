package com.roboleague.api.graphql.view;

import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsVersion;

/**
 * GraphQL view of a standings version without its rows, for the list of versions.
 */
public record StandingsVersionSummaryView(int number, String calculatedAt, String status,
                                          PublicationView publication) {

    public static StandingsVersionSummaryView from(Standings standings, StandingsVersion version) {
        return new StandingsVersionSummaryView(version.number(), Dates.iso(version.calculatedAt()),
                standings.statusOf(version).name(),
                standings.publicationOf(version).map(PublicationView::from).orElse(null));
    }
}
