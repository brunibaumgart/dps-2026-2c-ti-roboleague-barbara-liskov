package com.roboleague.tournament;

import java.util.Objects;

/**
 * Value object representing edition identification details.
 */
public record EditionHeader(EditionId id, String name, int editionNumber) {
    public EditionHeader {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("edition name cannot be blank");
        }
        if (editionNumber <= 0) {
            throw new IllegalArgumentException("editionNumber must be positive");
        }
    }

    public static EditionHeader of(EditionId id, String name, int editionNumber) {
        return new EditionHeader(id, name, editionNumber);
    }
}
