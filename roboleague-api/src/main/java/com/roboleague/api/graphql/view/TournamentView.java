package com.roboleague.api.graphql.view;

import com.roboleague.tournament.Tournament;

/**
 * GraphQL view of the tournament an edition belongs to.
 */
public record TournamentView(String id, String name, String description, SeasonView season) {

    public static TournamentView from(Tournament tournament) {
        return new TournamentView(tournament.id(), tournament.name(), tournament.description(),
                SeasonView.from(tournament.season()));
    }
}
