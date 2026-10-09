package com.roboleague.ranking;

import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;

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

    public static TeamEntryHeader of(TeamId teamId, String teamName, CategoryId categoryId, EditionId editionId) {
        return new TeamEntryHeader(
                new TeamIdentity(teamId, teamName),
                new CompetitionContext(categoryId, editionId)
        );
    }
}
