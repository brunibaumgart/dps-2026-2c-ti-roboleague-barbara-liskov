package com.roboleague.tournament;

import java.util.Objects;

/**
 * Value object representing participant personal identity.
 */
public record MemberProfile(String id, String fullName) {
    public MemberProfile {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(fullName, "fullName cannot be null");
    }

    public static MemberProfile of(String id, String fullName) {
        return new MemberProfile(id, fullName);
    }
}
