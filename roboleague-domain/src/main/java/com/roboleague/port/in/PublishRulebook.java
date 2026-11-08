package com.roboleague.port.in;

import com.roboleague.evaluation.Rulebook;
import com.roboleague.evaluation.definition.RulebookDefinition;
import com.roboleague.tournament.ChallengeId;

/**
 * Publishes the next version of a challenge's rulebook. Earlier versions stay as they were. The new version applies
 * to what is played after it: a category that already has results keeps the version that scored them, and one that
 * does not will play with the new version, so the new version has to admit the rounds already scheduled for it.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface PublishRulebook {
    Publication<Rulebook> execute(ChallengeId challengeId, RulebookDefinition definition);
}
