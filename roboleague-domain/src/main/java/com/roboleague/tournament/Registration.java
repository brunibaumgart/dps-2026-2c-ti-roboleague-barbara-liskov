package com.roboleague.tournament;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/** Enrollment identified by edition/team; category and calendar are local to that edition. */
public record Registration(TeamId teamId, EditionId editionId, CategoryId categoryId,
                           LocalDate referenceDate, LocalDateTime registeredAt) {
    public Registration {
        Objects.requireNonNull(teamId, "teamId cannot be null");
        Objects.requireNonNull(editionId, "editionId cannot be null");
        Objects.requireNonNull(categoryId, "categoryId cannot be null");
        Objects.requireNonNull(referenceDate, "referenceDate cannot be null");
        Objects.requireNonNull(registeredAt, "registeredAt cannot be null");
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Registration registration
                && teamId.equals(registration.teamId) && editionId.equals(registration.editionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(editionId, teamId);
    }
}
