package com.roboleague.port.in;

import com.roboleague.tournament.Team;
import com.roboleague.tournament.TeamId;
import java.util.Collection;
import java.util.Map;

/**
 * Reads the canonical teams: one by id, or many at once for whoever got their ids from registrations, slots or
 * standings. Reads only.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface QueryTeams {
    /** The canonical team, or NotFoundException("Team", id). One read. */
    Team get(TeamId id);

    /**
     * The teams with those ids, keyed by id, in one read however many ids there are (none for an empty collection).
     * A stored reference that does not exist is an InconsistentStateException: the caller got the ids from
     * registrations, slots or standings.
     */
    Map<TeamId, Team> byIds(Collection<TeamId> ids);
}
