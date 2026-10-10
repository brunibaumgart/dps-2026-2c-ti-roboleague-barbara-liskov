package com.roboleague.usecase;

import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.StandingsPublication;
import com.roboleague.repository.StandingsRepository;
import com.roboleague.support.Clock;

import java.util.Objects;

/**
 * Makes a version of the standings official. The standings check it against the competition as it is now: when
 * something blocks it nothing is stored and every reason comes back.
 */
public class PublishStandingsUseCase {
    private final CategoryResultsReader results;
    private final StandingsRepository standings;
    private final Clock clock;

    public PublishStandingsUseCase(CategoryResultsReader results, StandingsRepository standings, Clock clock) {
        this.results = Objects.requireNonNull(results, "results cannot be null");
        this.standings = Objects.requireNonNull(standings, "standings cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public StandingsPublication execute(StandingsId id, int version, AuditNote note) {
        Standings calculated = standings.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Standings not calculated yet: " + id));
        StandingsPublication publication = calculated.publish(version, results.read(id).publicationCheck(), note,
                clock.now());
        switch (publication) {
            case StandingsPublication.Published published -> standings.save(calculated);
            case StandingsPublication.Blocked blocked -> {
            }
        }
        return publication;
    }
}
