package com.roboleague.tournament;

import java.util.Objects;

/**
 * Value object representing edition identification details.
 */
public record EditionHeader(String id, String name, int editionNumber) {
    public EditionHeader {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        if (editionNumber <= 0) {
            throw new IllegalArgumentException("editionNumber must be positive");
        }
    }

    public static EditionHeader of(String id, String name, int editionNumber) {
        return new EditionHeader(id, name, editionNumber);
    }
}
