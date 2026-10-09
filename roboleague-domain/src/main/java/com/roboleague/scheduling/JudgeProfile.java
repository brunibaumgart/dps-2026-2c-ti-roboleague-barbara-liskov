package com.roboleague.scheduling;

import java.util.Objects;

/**
 * Value object representing personal identification of a judge.
 */
public record JudgeProfile(JudgeId id, String fullName) {
    public JudgeProfile {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(fullName, "fullName cannot be null");
    }

    public static JudgeProfile of(JudgeId id, String fullName) {
        return new JudgeProfile(id, fullName);
    }
}
