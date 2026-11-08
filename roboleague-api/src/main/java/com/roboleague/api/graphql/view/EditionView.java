package com.roboleague.api.graphql.view;

import com.roboleague.tournament.Edition;

/**
 * GraphQL view of an edition: the scalar fields of the schema type. Relations are resolved by {@code EditionQueries}.
 */
public record EditionView(String id, String name, int editionNumber, String startDate, String endDate) {

    public static EditionView from(Edition edition) {
        return new EditionView(edition.getId().value(), edition.getName(), edition.getEditionNumber(),
                edition.getStartDate().toString(), edition.getEndDate().toString());
    }
}
