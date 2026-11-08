package com.roboleague.port.in;

import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.RulebookVersion;
import com.roboleague.tournament.ChallengeId;

/**
 * Reads a published historical version without republishing or changing it.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface GetRulebook {
    Rulebook execute(ChallengeId challengeId, RulebookVersion version);
}
