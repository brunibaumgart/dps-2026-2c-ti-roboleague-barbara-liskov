package com.roboleague.api.graphql;

import com.roboleague.api.graphql.view.AppealView;
import com.roboleague.api.graphql.view.ChallengeView;
import com.roboleague.api.graphql.view.EditionView;
import com.roboleague.api.graphql.view.RoundView;
import com.roboleague.api.graphql.view.RulebookView;
import com.roboleague.api.graphql.view.StandingsView;
import com.roboleague.port.in.GetChallenge;
import com.roboleague.port.in.QueryAppeals;
import com.roboleague.port.in.QueryEditions;
import com.roboleague.port.in.QueryRounds;
import com.roboleague.port.in.QueryStandings;
import com.roboleague.ranking.StandingsId;
import com.roboleague.tournament.CategoryId;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves {@code Query.challenge} and the relations of {@code Challenge}. Same use cases as the REST controllers;
 * this adapter only translates. The rulebooks travel in the view, so {@code currentRulebook}, {@code rulebooks} and
 * {@code rulebook(version)} cost no read; {@code edition} is one batch for every challenge of the response; rounds,
 * standings and appeals are one read per challenge (README of Entrega 3, "Estrategia por relación").
 */
@Controller
class ChallengeQueries {

    private final GetChallenge getChallenge;
    private final QueryEditions queryEditions;
    private final QueryRounds queryRounds;
    private final QueryStandings queryStandings;
    private final QueryAppeals queryAppeals;

    ChallengeQueries(GetChallenge getChallenge, QueryEditions queryEditions,
                     QueryRounds queryRounds, QueryStandings queryStandings,
                     QueryAppeals queryAppeals) {
        this.getChallenge = Objects.requireNonNull(getChallenge);
        this.queryEditions = Objects.requireNonNull(queryEditions);
        this.queryRounds = Objects.requireNonNull(queryRounds);
        this.queryStandings = Objects.requireNonNull(queryStandings);
        this.queryAppeals = Objects.requireNonNull(queryAppeals);
    }

    @QueryMapping
    ChallengeView challenge(@Argument String id) {
        return ChallengeView.from(getChallenge.execute(ChallengeId.of(id)));
    }

    /** One read for the editions of every challenge in the response, however many challenges there are. */
    @BatchMapping(typeName = "Challenge", field = "edition")
    Map<ChallengeView, EditionView> edition(List<ChallengeView> challenges) {
        Map<EditionId, Edition> editions = queryEditions.byIds(
                challenges.stream().map(challenge -> EditionId.of(challenge.editionId())).distinct().toList());
        // Two equal views (the same challenge reached twice) are one key: keep the first.
        Map<ChallengeView, EditionView> byChallenge = new LinkedHashMap<>();
        challenges.forEach(challenge -> byChallenge.putIfAbsent(challenge,
                EditionView.from(editions.get(EditionId.of(challenge.editionId())))));
        return byChallenge;
    }

    /** A version the challenge never published is null without an error: it filters, it does not look up an id. */
    @SchemaMapping(typeName = "Challenge", field = "rulebook")
    RulebookView rulebook(ChallengeView challenge, @Argument int version) {
        return challenge.rulebook(version).orElse(null);
    }

    @SchemaMapping(typeName = "Challenge", field = "rounds")
    List<RoundView> rounds(ChallengeView challenge, @Argument String categoryId) {
        return queryRounds.list(ChallengeId.of(challenge.id()), Optional.ofNullable(categoryId).map(CategoryId::of))
                .stream().map(RoundView::from).toList();
    }

    @SchemaMapping(typeName = "Challenge", field = "standings")
    StandingsView standings(ChallengeView challenge, @Argument String categoryId) {
        StandingsId id = standingsId(challenge, categoryId);
        return StandingsView.from(id, queryStandings.execute(id));
    }

    @SchemaMapping(typeName = "Challenge", field = "appeals")
    List<AppealView> appeals(ChallengeView challenge, @Argument String categoryId) {
        return queryAppeals.ofCategory(standingsId(challenge, categoryId)).stream().map(AppealView::from).toList();
    }

    private static StandingsId standingsId(ChallengeView challenge, String categoryId) {
        return StandingsId.of(ChallengeId.of(challenge.id()), CategoryId.of(categoryId));
    }
}
