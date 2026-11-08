package com.roboleague.port.in;

import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;

/**
 * Replaces canonical immutable state only after validating every enrollment.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface UpdateTeam {
    Team execute(TeamId teamId, Team candidate);
}
