package com.roboleague.port.in;

import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.support.ActorId;

/**
 * Use case to transition an appeal into UNDER_REVIEW by an assigned arbitrator/judge.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface ReviewAppeal {
    Appeal execute(AppealId appealId, ActorId reviewerId);
}
