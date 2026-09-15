package com.roboleague.tournament;

import java.util.Objects;

/**
 * Technical competition category with defined constraints and limits.
 */
public record Category(
        String id,
        String name,
        String description,
        int minTeamMembers,
        int maxTeamMembers,
        int minAge,
        int maxAge,
        double maxRobotWeightGrams,
        double maxRobotLengthMm,
        double maxRobotWidthMm,
        double maxRobotHeightMm
) {
    public Category {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        if (minTeamMembers <= 0) {
            throw new IllegalArgumentException("minTeamMembers must be positive");
        }
        if (maxTeamMembers < minTeamMembers) {
            throw new IllegalArgumentException("maxTeamMembers cannot be less than minTeamMembers");
        }
        if (minAge < 0 || maxAge < minAge) {
            throw new IllegalArgumentException("Invalid age limits");
        }
        if (maxRobotWeightGrams <= 0) {
            throw new IllegalArgumentException("maxRobotWeightGrams must be positive");
        }
    }

    public static Category of(String id, String name, int minMembers, int maxMembers, int minAge, int maxAge, double maxWeightGrams) {
        return new Category(id, name, "", minMembers, maxMembers, minAge, maxAge, maxWeightGrams, 500, 500, 500);
    }
}
