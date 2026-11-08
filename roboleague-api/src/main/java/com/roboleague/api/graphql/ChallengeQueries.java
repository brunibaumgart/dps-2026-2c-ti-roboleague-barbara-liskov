package com.roboleague.api.graphql;

import com.roboleague.api.graphql.view.ChallengeView;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.usecase.GetChallengeUseCase;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.Objects;

/**
 * Resolves {@code Query.challenge}. Same use case as {@code ChallengeController}; this adapter only translates. A
 * missing id is a {@code NotFoundException}: the field is null and {@code errors} says NOT_FOUND.
 */
@Controller
class ChallengeQueries {

    private final GetChallengeUseCase getChallenge;

    ChallengeQueries(GetChallengeUseCase getChallenge) {
        this.getChallenge = Objects.requireNonNull(getChallenge);
    }

    @QueryMapping
    ChallengeView challenge(@Argument String id) {
        return ChallengeView.from(getChallenge.execute(ChallengeId.of(id)));
    }
}
