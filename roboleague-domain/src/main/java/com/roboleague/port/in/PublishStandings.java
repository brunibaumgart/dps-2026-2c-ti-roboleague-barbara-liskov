package com.roboleague.port.in;

import com.roboleague.evaluation.audit.AuditNote;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.StandingsPublication;

/**
 * Makes a version of the standings official. The standings check it against the competition as it is now: when
 * something blocks it nothing is stored and every reason comes back.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface PublishStandings {
    StandingsPublication execute(StandingsId id, int version, AuditNote note);
}
