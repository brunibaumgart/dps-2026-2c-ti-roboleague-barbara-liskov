package com.roboleague.api.graphql;

import com.roboleague.api.graphql.view.AppealView;
import com.roboleague.api.graphql.view.AttemptView;
import com.roboleague.api.graphql.view.StandingsView;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.port.in.QueryAppeals;
import com.roboleague.port.in.QueryStandings;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Objects;

/**
 * Resolves the ranking reads: {@code Query.standings}, {@code Query.appeal} and {@code Attempt.appeals}. Same use
 * cases as {@code StandingsController} and {@code AppealController}; this adapter only translates. A category the challenge's
 * edition does not offer is an {@code IllegalArgumentException} from the domain, so it answers BAD_REQUEST.
 */
@Controller
class StandingsQueries {

    private final QueryStandings queryStandings;
    private final QueryAppeals queryAppeals;

    StandingsQueries(QueryStandings queryStandings, QueryAppeals queryAppeals) {
        this.queryStandings = Objects.requireNonNull(queryStandings);
        this.queryAppeals = Objects.requireNonNull(queryAppeals);
    }

    @QueryMapping
    StandingsView standings(@Argument String challengeId, @Argument String categoryId) {
        StandingsId id = StandingsId.of(ChallengeId.of(challengeId), CategoryId.of(categoryId));
        return StandingsView.from(id, queryStandings.execute(id));
    }

    @QueryMapping
    AppealView appeal(@Argument String id) {
        return AppealView.from(queryAppeals.get(AppealId.of(id)));
    }

    /** A list per parent: one read per attempt, never one per appeal. */
    @SchemaMapping(typeName = "Attempt", field = "appeals")
    List<AppealView> attemptAppeals(AttemptView attempt) {
        return queryAppeals.ofAttempt(AttemptId.parse(attempt.id())).stream().map(AppealView::from).toList();
    }
}
