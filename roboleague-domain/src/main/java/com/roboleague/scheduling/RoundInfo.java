package com.roboleague.scheduling;

import java.util.Objects;

/**
 * Value object grouping identification, name and scope of a competition round.
 */
public record RoundInfo(RoundId id, String name, RoundScope scope) {
    public RoundInfo {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(scope, "scope cannot be null");
    }

    public static RoundInfo of(RoundId id, String name, RoundScope scope) {
        return new RoundInfo(id, name, scope);
    }
}
