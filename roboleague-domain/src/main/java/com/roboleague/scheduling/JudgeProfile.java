package com.roboleague.scheduling;

import java.util.Objects;

/**
 * Value object representing personal identification of a judge.
 */
public record JudgeProfile(String id, String fullName) {
    public JudgeProfile {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(fullName, "fullName cannot be null");
    }

    public static JudgeProfile of(String id, String fullName) {
        return new JudgeProfile(id, fullName);
    }
}
