package com.roboleague.tournament;

import java.util.Objects;

/**
 * Value object contextualizing an edition within a tournament.
 */
public record EditionContext(Tournament tournament, EditionHeader header) {
    public EditionContext {
        Objects.requireNonNull(tournament, "tournament cannot be null");
        Objects.requireNonNull(header, "header cannot be null");
    }

    public static EditionContext of(Tournament tournament, EditionHeader header) {
        return new EditionContext(tournament, header);
    }
}
