package com.roboleague.tournament;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Team member participant.
 */
public record TeamMember(
        String id,
        String fullName,
        LocalDate birthDate,
        String role
) {
    public TeamMember {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(fullName, "fullName cannot be null");
        Objects.requireNonNull(birthDate, "birthDate cannot be null");
        if (birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("birthDate cannot be in the future");
        }
    }

    public int getAgeAt(LocalDate referenceDate) {
        Objects.requireNonNull(referenceDate, "referenceDate cannot be null");
        return Period.between(birthDate, referenceDate).getYears();
    }
}
