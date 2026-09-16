package com.roboleague.tournament;

import java.util.Objects;

/**
 * Value object grouping all regulatory category constraints.
 */
public record CategoryRestrictions(
        TeamSizeRange teamSize,
        AgeRange ageRange,
        RobotLimits robotLimits
) {
    public CategoryRestrictions {
        Objects.requireNonNull(teamSize, "teamSize cannot be null");
        Objects.requireNonNull(ageRange, "ageRange cannot be null");
        Objects.requireNonNull(robotLimits, "robotLimits cannot be null");
    }

    public static CategoryRestrictions of(TeamSizeRange teamSize, AgeRange ageRange, RobotLimits robotLimits) {
        return new CategoryRestrictions(teamSize, ageRange, robotLimits);
    }
}
