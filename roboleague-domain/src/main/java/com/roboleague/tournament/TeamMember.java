package com.roboleague.tournament;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Team member participant.
 */
public record TeamMember(
        MemberProfile profile,
        LocalDate birthDate,
        String role
) {
    public TeamMember {
        Objects.requireNonNull(profile, "profile cannot be null");
        Objects.requireNonNull(birthDate, "birthDate cannot be null");
        role = role != null ? role : "MEMBER";
        if (birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("birthDate cannot be in the future");
        }
    }

    public String id() {
        return profile.id();
    }

    public String fullName() {
        return profile.fullName();
    }

    public int getAgeAt(LocalDate referenceDate) {
        Objects.requireNonNull(referenceDate, "referenceDate cannot be null");
        return Period.between(birthDate, referenceDate).getYears();
    }

    public static TeamMember of(MemberProfile profile, LocalDate birthDate, String role) {
        return new TeamMember(profile, birthDate, role);
    }

    public static TeamMember of(String id, String fullName, LocalDate birthDate, String role) {
        return new TeamMember(new MemberProfile(id, fullName), birthDate, role);
    }
}
