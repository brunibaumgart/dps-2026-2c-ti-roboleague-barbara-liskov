package com.roboleague.tournament;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Team member participant. Business creation uses {@code of} with an explicit
 * reference date; the value constructor restores structural data without consulting today's calendar.
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
    }

    public ParticipantId id() {
        return profile.id();
    }

    public String fullName() {
        return profile.fullName();
    }

    public int getAgeAt(LocalDate referenceDate) {
        Objects.requireNonNull(referenceDate, "referenceDate cannot be null");
        return Period.between(birthDate, referenceDate).getYears();
    }

    public static TeamMember of(MemberProfile profile, LocalDate birthDate, String role, LocalDate referenceDate) {
        Objects.requireNonNull(referenceDate, "referenceDate cannot be null");
        if (birthDate.isAfter(referenceDate)) {
            throw new IllegalArgumentException("birthDate cannot be in the future");
        }
        return new TeamMember(profile, birthDate, role);
    }

    public static TeamMember of(ParticipantId id, String fullName, LocalDate birthDate, String role, LocalDate referenceDate) {
        return of(new MemberProfile(id, fullName), birthDate, role, referenceDate);
    }
}
