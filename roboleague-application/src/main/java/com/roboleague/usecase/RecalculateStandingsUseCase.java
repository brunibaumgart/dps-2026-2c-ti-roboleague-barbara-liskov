package com.roboleague.usecase;

import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.StandingsTable;
import com.roboleague.repository.StandingsRepository;
import com.roboleague.support.Clock;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Calculates the standings of a challenge in a category with the results as they are now. The first calculation
 * creates them; each later one adds a provisional version and keeps the previous ones as they were.
 */
public class RecalculateStandingsUseCase {
    private final CategoryResultsReader results;
    private final StandingsRepository standings;
    private final Clock clock;

    public RecalculateStandingsUseCase(CategoryResultsReader results, StandingsRepository standings, Clock clock) {
        this.results = Objects.requireNonNull(results, "results cannot be null");
        this.standings = Objects.requireNonNull(standings, "standings cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public Standings execute(StandingsId id) {
        StandingsTable table = results.read(id).table();
        LocalDateTime now = clock.now();
        Standings calculated = standings.findById(id)
                .map(existing -> {
                    existing.recalculate(table, now);
                    return existing;
                })
                .orElseGet(() -> Standings.first(id, table, now));
        standings.save(calculated);
        return calculated;
    }
}
