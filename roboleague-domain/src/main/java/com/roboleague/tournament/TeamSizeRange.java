package com.roboleague.tournament;

/**
 * Value object representing allowable team size bounds.
 */
public record TeamSizeRange(int minMembers, int maxMembers) {
    public TeamSizeRange {
        if (minMembers <= 0) {
            throw new IllegalArgumentException("minMembers must be positive");
        }
        if (maxMembers < minMembers) {
            throw new IllegalArgumentException("maxMembers cannot be less than minMembers");
        }
    }

    public boolean allows(int memberCount) {
        return memberCount >= minMembers && memberCount <= maxMembers;
    }

    public static TeamSizeRange of(int minMembers, int maxMembers) {
        return new TeamSizeRange(minMembers, maxMembers);
    }
}
