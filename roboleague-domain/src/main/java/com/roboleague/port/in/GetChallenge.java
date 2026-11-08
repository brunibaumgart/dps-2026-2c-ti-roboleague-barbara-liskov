package com.roboleague.port.in;

import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import java.util.Collection;
import java.util.Map;

/**
 * Input port of GetChallenge.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface GetChallenge {
    Challenge execute(ChallengeId challengeId);

    /**
     * The challenges with those ids, keyed by id, in one read however many ids there are (none for an empty
     * collection). A stored reference that does not exist is an InconsistentStateException.
     */
    Map<ChallengeId, Challenge> byIds(Collection<ChallengeId> challengeIds);
}
