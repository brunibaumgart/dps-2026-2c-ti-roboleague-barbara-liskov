package com.roboleague.port.in;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.tournament.TeamId;

/**
 * Use case to file an appeal against an attempt result.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface FileAppeal {
    Appeal execute(AttemptId attemptId, TeamId teamId, String reason, String evidenceDescription);
}
