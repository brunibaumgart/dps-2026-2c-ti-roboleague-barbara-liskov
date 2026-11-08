package com.roboleague.port.in;

import com.roboleague.ranking.StandingsId;

/**
 * The standings of a challenge in a category, if they were calculated, next to what would block publishing them
 * right now.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface QueryStandings {
    StandingsOverview execute(StandingsId id);

    /**
     * One calculated version, next to the standings it belongs to (they know whether it is official).
     */
    CalculatedVersion version(StandingsId id, int number);
}
