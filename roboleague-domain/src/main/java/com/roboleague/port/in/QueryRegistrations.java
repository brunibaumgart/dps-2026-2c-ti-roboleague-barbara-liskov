package com.roboleague.port.in;

import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;
import java.util.List;

/**
 * Input port of QueryRegistrations.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface QueryRegistrations {
    List<RegisteredTeam> list(EditionId editionId);

    RegisteredTeam get(EditionId editionId, TeamId teamId);
}
