package com.roboleague.port.in;

import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.Registration;
import com.roboleague.tournament.TeamId;

/**
 * Enrolls new or canonical teams without overwriting an existing team's state.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface RegisterTeam {
    /** Registers a new team: the use case builds it from the candidate with the server clock. */
    Registration execute(EditionId editionId, CategoryId categoryId, TeamCandidate candidate);

    Registration execute(EditionId editionId, CategoryId categoryId, TeamId teamId);
}
