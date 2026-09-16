package com.roboleague.tournament;

import java.util.Objects;

/**
 * Technical competition category with defined constraints and limits.
 */
public record Category(
        String id,
        String name,
        CategoryRestrictions restrictions
) {
    public Category {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(restrictions, "restrictions cannot be null");
    }

    public int minTeamMembers() {
        return restrictions.teamSize().minMembers();
    }

    public int maxTeamMembers() {
        return restrictions.teamSize().maxMembers();
    }

    public int minAge() {
        return restrictions.ageRange().minAge();
    }

    public int maxAge() {
        return restrictions.ageRange().maxAge();
    }

    public double maxRobotWeightGrams() {
        return restrictions.robotLimits().maxWeight().grams();
    }

    public double maxRobotLengthMm() {
        return restrictions.robotLimits().maxDimensions().lengthMm();
    }

    public double maxRobotWidthMm() {
        return restrictions.robotLimits().maxDimensions().widthMm();
    }

    public double maxRobotHeightMm() {
        return restrictions.robotLimits().maxDimensions().heightMm();
    }

    public boolean allowsTeamSize(int memberCount) {
        return restrictions.teamSize().allows(memberCount);
    }

    public boolean allowsAge(int age) {
        return restrictions.ageRange().contains(age);
    }

    public boolean allowsRobot(RobotSpecification spec) {
        return restrictions.robotLimits().allows(spec);
    }

    public static Category of(String id, String name, CategoryRestrictions restrictions) {
        return new Category(id, name, restrictions);
    }

    public static Category of(String id, String name, int minMembers, int maxMembers, int minAge, int maxAge, double maxWeightGrams) {
        return of(id, name, minMembers, maxMembers, minAge, maxAge, maxWeightGrams, 1000.0, 1000.0, 1000.0);
    }

    public static Category of(String id, String name, int minMembers, int maxMembers, int minAge, int maxAge,
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
