package com.roboleague.tournament;

import java.util.Objects;

/**
 * Technical competition category with defined constraints and limits.
 */
public record Category(
        CategoryId id,
        String name,
        CategoryRestrictions restrictions
) {
    public Category {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(restrictions, "restrictions cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("category name cannot be blank");
        }
    }

    public static Category of(CategoryId id, String name, CategoryRestrictions restrictions) {
        return new Category(id, name, restrictions);
    }

    public static Category of(CategoryId id, String name, int minMembers, int maxMembers, int minAge, Integer maxAge, double maxWeightGrams) {
        return of(id, name, minMembers, maxMembers, minAge, maxAge, maxWeightGrams, 1000.0, 1000.0, 1000.0);
    }

    public static Category of(CategoryId id, String name, int minMembers, int maxMembers, int minAge, Integer maxAge,
                              double maxWeightGrams, double maxLenMm, double maxWidMm, double maxHgtMm) {
        return new Category(
                id,
                name,
                new CategoryRestrictions(
                        new TeamSizeRange(minMembers, maxMembers),
                        new AgeRange(minAge, maxAge),
                        RobotLimits.of(maxWeightGrams, maxLenMm, maxWidMm, maxHgtMm)
                )
        );
    }
}
