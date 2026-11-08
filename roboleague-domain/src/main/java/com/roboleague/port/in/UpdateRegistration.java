package com.roboleague.port.in;

import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.TeamId;

/**
 * Validates a combined team/category candidate before either repository is written.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface UpdateRegistration {
    /** Replaces the team and its category in one validated step; the team is built with the server clock. */
    RegisteredTeam execute(EditionId editionId, TeamId teamId, CategoryId categoryId, TeamCandidate candidate);
}
