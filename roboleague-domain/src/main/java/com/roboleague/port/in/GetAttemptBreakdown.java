package com.roboleague.port.in;

import com.roboleague.evaluation.AttemptId;
import java.util.Collection;
import java.util.Map;

/**
 * Explains an attempt with the rulebook version it is scored with: what is still pending and, once scored, how
 * each source contributed.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface GetAttemptBreakdown {
    AttemptBreakdown execute(AttemptId attemptId);

    /**
     * The explained attempts with those ids, keyed by id, in two reads however many attempts or challenges there
     * are: the attempts and their challenges (none for an empty collection). A stored reference that does not exist
     * is an InconsistentStateException.
     */
    Map<AttemptId, AttemptBreakdown> byIds(Collection<AttemptId> attemptIds);
}
