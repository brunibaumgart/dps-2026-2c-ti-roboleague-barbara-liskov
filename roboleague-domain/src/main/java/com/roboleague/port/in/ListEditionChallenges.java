package com.roboleague.port.in;

import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.EditionId;
import java.util.List;

/**
 * Input port of ListEditionChallenges.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface ListEditionChallenges {
    List<Challenge> execute(EditionId id);
}
