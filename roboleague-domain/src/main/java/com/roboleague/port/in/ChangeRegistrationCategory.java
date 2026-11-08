package com.roboleague.port.in;

import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.EditionId;
import com.roboleague.tournament.Registration;
import com.roboleague.tournament.TeamId;

/**
 * Changes the category of one enrollment, preserving its identity, calendar and original timestamp.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface ChangeRegistrationCategory {
    Registration execute(EditionId editionId, TeamId teamId, CategoryId categoryId);
}
