package com.roboleague.api.graphql.view;

import com.roboleague.ranking.OfficialPublication;

/**
 * GraphQL view of who made a standings version official, when and with which note.
 */
public record PublicationView(String publishedAt, String publishedBy, String notes) {

    public static PublicationView from(OfficialPublication publication) {
        return new PublicationView(Dates.iso(publication.publishedAt()), publication.note().authorId().value(),
                publication.note().reason());
    }
}
