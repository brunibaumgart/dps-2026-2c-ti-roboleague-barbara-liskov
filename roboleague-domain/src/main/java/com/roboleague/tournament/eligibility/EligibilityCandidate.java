package com.roboleague.tournament.eligibility;

import com.roboleague.tournament.Category;
import com.roboleague.tournament.Team;

import java.time.LocalDate;
import java.util.Objects;

/** All facts needed by an eligibility rule, independent of the host's calendar. */
public record EligibilityCandidate(Team team, Category category, LocalDate referenceDate) {
    public EligibilityCandidate {
        Objects.requireNonNull(team, "team cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(referenceDate, "referenceDate cannot be null");
    }
}
