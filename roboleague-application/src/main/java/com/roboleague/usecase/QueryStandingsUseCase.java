package com.roboleague.usecase;

import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.StandingsVersion;
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

    /**
     * One calculated version, next to the standings it belongs to (they know whether it is official).
     */
    public CalculatedVersion version(StandingsId id, int number) {
        Standings calculated = standings.findById(id)
                .orElseThrow(() -> new NotFoundException("Standings", id, "Standings not calculated yet: " + id));
        StandingsVersion version = calculated.version(number)
                .orElseThrow(() -> new NotFoundException("Standings version", number,
                        "Standings " + id + " have no version " + number));
        return new CalculatedVersion(calculated, version);
    }

    public record CalculatedVersion(Standings standings, StandingsVersion version) {
        public CalculatedVersion {
            Objects.requireNonNull(standings, "standings cannot be null");
            Objects.requireNonNull(version, "version cannot be null");
        }
    }
}
