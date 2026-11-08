package com.roboleague.port.in;

import com.roboleague.ranking.Standings;
import com.roboleague.ranking.StandingsId;

/**
 * Calculates the standings of a challenge in a category with the results as they are now. The first calculation
 * creates them; each later one adds a provisional version and keeps the previous ones as they were.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface RecalculateStandings {
    Standings execute(StandingsId id);
}
