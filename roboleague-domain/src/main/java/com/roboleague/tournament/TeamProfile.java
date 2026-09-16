package com.roboleague.tournament;

import java.util.Objects;

/**
 * Value object representing team identity information.
 */
public record TeamProfile(String id, String name, String institution) {
    public TeamProfile {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        institution = institution != null ? institution : "";
    }

    public static TeamProfile of(String id, String name, String institution) {
        return new TeamProfile(id, name, institution);
    }
}
