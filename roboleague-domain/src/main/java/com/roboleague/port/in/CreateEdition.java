package com.roboleague.port.in;

import com.roboleague.tournament.Edition;

/**
 * Configures an event: a new edition of a tournament with its dates and categories.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface CreateEdition {
    Edition execute(CreateEditionCommand command);
}
