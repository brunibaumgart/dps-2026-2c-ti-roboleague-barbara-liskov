package com.roboleague.scheduling;

import java.util.Objects;

/**
 * Competition judge responsible for officiating and evaluating attempts.
 */
public record Judge(
        JudgeProfile profile,
        String specialty,
        String certificationLevel
) {
    public Judge {
        Objects.requireNonNull(profile, "profile cannot be null");
        specialty = specialty != null ? specialty : "GENERAL";
        certificationLevel = certificationLevel != null ? certificationLevel : "CERTIFIED";
    }

    public String id() {
        return profile.id();
    }

    public String fullName() {
        return profile.fullName();
    }

    public static Judge of(JudgeProfile profile, String specialty, String certificationLevel) {
        return new Judge(profile, specialty, certificationLevel);
    }

    public static Judge of(String id, String fullName, String specialty) {
        return new Judge(new JudgeProfile(id, fullName), specialty, "CERTIFIED");
    }
}
