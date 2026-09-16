package com.roboleague.ranking;

import java.util.Objects;

/**
 * Value object grouping team identification and category context.
 */
public record TeamEntryHeader(TeamIdentity team, CompetitionContext context) {
    public TeamEntryHeader {
        Objects.requireNonNull(team, "team cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
    }

    public static TeamEntryHeader of(TeamIdentity team, CompetitionContext context) {
        return new TeamEntryHeader(team, context);
    }

    public static TeamEntryHeader of(String teamId, String teamName, String categoryId, String editionId) {
        return new TeamEntryHeader(
                new TeamIdentity(teamId, teamName),
                new CompetitionContext(categoryId, editionId)
        );
    }
}
