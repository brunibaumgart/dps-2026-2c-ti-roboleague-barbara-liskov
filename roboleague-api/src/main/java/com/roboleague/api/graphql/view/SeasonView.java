package com.roboleague.api.graphql.view;

import com.roboleague.tournament.Season;

/**
 * GraphQL view of a season.
 */
public record SeasonView(String id, int year, String name) {

    public static SeasonView from(Season season) {
        return new SeasonView(season.id(), season.year(), season.name());
    }
}
