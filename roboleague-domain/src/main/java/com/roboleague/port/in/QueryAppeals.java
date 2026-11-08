package com.roboleague.port.in;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealId;
import java.util.List;

/**
 * Appeals by id, of one attempt, or of every attempt a challenge ran in a category; oldest first.
 *
 * <p>Input port: adapters (REST, GraphQL) depend on this interface, never on its implementation.
 */
public interface QueryAppeals {
    Appeal get(AppealId id);

    List<Appeal> ofAttempt(AttemptId attemptId);

    List<Appeal> ofCategory(StandingsId id);
}
