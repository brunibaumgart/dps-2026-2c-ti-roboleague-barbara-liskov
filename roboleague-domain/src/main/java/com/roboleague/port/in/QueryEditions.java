package com.roboleague.port.in;

import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Input port of QueryEditions.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface QueryEditions {
    List<Edition> list();

    Edition get(EditionId id);

    /**
     * The editions with those ids, keyed by id, in one read however many ids there are (none for an empty
     * collection). A stored reference that does not exist is an InconsistentStateException.
     */
    Map<EditionId, Edition> byIds(Collection<EditionId> ids);
}
