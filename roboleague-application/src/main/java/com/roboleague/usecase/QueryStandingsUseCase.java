package com.roboleague.usecase;

import com.roboleague.ranking.StandingsId;
import com.roboleague.repository.StandingsRepository;

import java.util.Objects;

/**
 * The standings of a challenge in a category, if they were calculated, next to what would block publishing them
 * right now.
 */
public class QueryStandingsUseCase {
    private final CategoryResultsReader results;
    private final StandingsRepository standings;

    public QueryStandingsUseCase(CategoryResultsReader results, StandingsRepository standings) {
        this.results = Objects.requireNonNull(results, "results cannot be null");
        this.standings = Objects.requireNonNull(standings, "standings cannot be null");
    }

    public StandingsOverview execute(StandingsId id) {
        return new StandingsOverview(standings.findById(id), results.read(id).publicationCheck());
    }
}
