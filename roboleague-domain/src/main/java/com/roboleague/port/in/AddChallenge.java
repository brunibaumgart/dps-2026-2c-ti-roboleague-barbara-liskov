package com.roboleague.port.in;

import com.roboleague.tournament.Challenge;

/**
 * Adds a challenge to an edition by publishing its first rulebook. A definition with problems is rejected,
 * not thrown: whoever sent it gets every problem back.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface AddChallenge {
    Publication<Challenge> execute(AddChallengeCommand command);
}
