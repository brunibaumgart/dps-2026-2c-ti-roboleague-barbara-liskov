package com.roboleague.api.graphql;

import com.roboleague.api.graphql.view.AttemptView;
import com.roboleague.api.graphql.view.CategoryView;
import com.roboleague.api.graphql.view.ChallengeView;
import com.roboleague.api.graphql.view.RoundResultView;
import com.roboleague.api.graphql.view.RoundView;
import com.roboleague.api.graphql.view.SlotView;
import com.roboleague.api.graphql.view.TurnView;
import com.roboleague.port.in.GetChallenge;
import com.roboleague.port.in.InconsistentStateException;
import com.roboleague.port.in.QueryEditions;
import com.roboleague.port.in.QueryRounds;
import com.roboleague.port.in.QueryTurnAttempts;
import com.roboleague.port.in.TurnResults;
import com.roboleague.scheduling.Round;
import com.roboleague.scheduling.RoundId;
import com.roboleague.scheduling.SlotId;
import com.roboleague.tournament.Challenge;
import com.roboleague.tournament.ChallengeId;
import com.roboleague.tournament.Edition;
import com.roboleague.tournament.EditionId;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Resolves {@code Query.round}, {@code Query.slot} and the relations that lead to a round, its challenge, its category
 * and the capture state of its turns. Same use cases as {@code RoundController} and {@code ResultController}; this
 * adapter only translates. Every relation is one batch for the whole level of the response (README of Entrega 3,
 * "Estrategia por relación"): {@code turn} groups its four fields so the slots of a round cost one batch (D12).
 */
@Controller
class RoundQueries {

    private final QueryRounds queryRounds;
    private final GetChallenge getChallenge;
    private final QueryEditions queryEditions;
    private final QueryTurnAttempts queryTurns;

    RoundQueries(QueryRounds queryRounds, GetChallenge getChallenge, QueryEditions queryEditions,
                 QueryTurnAttempts queryTurns) {
        this.queryRounds = Objects.requireNonNull(queryRounds);
        this.getChallenge = Objects.requireNonNull(getChallenge);
        this.queryEditions = Objects.requireNonNull(queryEditions);
        this.queryTurns = Objects.requireNonNull(queryTurns);
    }

    @QueryMapping
    RoundView round(@Argument String id) {
        return RoundView.from(queryRounds.get(RoundId.of(id)));
    }

    /** The slot is found through its round, so the view keeps the round id that {@code turn} batches on. */
    @QueryMapping
    SlotView slot(@Argument String id) {
        SlotId slotId = SlotId.of(id);
        Round round = queryRounds.bySlot(slotId);
        return round.slot(slotId).map(slot -> SlotView.from(round, slot))
                .orElseThrow(() -> new InconsistentStateException("Round " + round.getId() + " lost slot " + slotId));
    }

    /** One read for the challenges of every round in the response. */
    @BatchMapping(typeName = "Round", field = "challenge")
    Map<RoundView, ChallengeView> challenge(List<RoundView> rounds) {
        Map<ChallengeId, Challenge> challenges = getChallenge.byIds(
                rounds.stream().map(round -> ChallengeId.of(round.challengeId())).distinct().toList());
        return byKey(rounds, round -> ChallengeView.from(challenges.get(ChallengeId.of(round.challengeId()))));
    }

    /**
     * One read for the editions of every round; the category comes from the loaded edition. A stored round whose
     * edition no longer offers its category is corrupt data, not a bad request.
     */
    @BatchMapping(typeName = "Round", field = "category")
    Map<RoundView, CategoryView> category(List<RoundView> rounds) {
        Map<EditionId, Edition> editions = queryEditions.byIds(
                rounds.stream().map(round -> EditionId.of(round.editionId())).distinct().toList());
        return byKey(rounds, round -> {
            Edition edition = editions.get(EditionId.of(round.editionId()));
            return edition.getCategories().stream()
                    .filter(category -> category.id().value().equals(round.categoryId()))
                    .findFirst().map(CategoryView::from)
                    .orElseThrow(() -> new InconsistentStateException("Category " + round.categoryId()
                            + " of round " + round.id() + " is not offered in edition " + edition.getId()));
        });
    }

    /** One call with the distinct rounds of the slots; the use case reads a fixed number per (challenge, category). */
    @BatchMapping(typeName = "Slot", field = "turn")
    Map<SlotView, TurnView> turn(List<SlotView> slots) {
        Map<SlotId, TurnResults> turns = queryTurns.ofRounds(
                slots.stream().map(slot -> RoundId.of(slot.roundId())).distinct().toList());
        return byKey(slots, slot -> TurnView.from(turns.get(SlotId.of(slot.id()))));
    }

    @BatchMapping(typeName = "Attempt", field = "round")
    Map<AttemptView, RoundView> attemptRound(List<AttemptView> attempts) {
        return rounds(attempts, AttemptView::roundId);
    }

    @BatchMapping(typeName = "RoundResult", field = "round")
    Map<RoundResultView, RoundView> roundResultRound(List<RoundResultView> results) {
        return rounds(results, RoundResultView::roundId);
    }

    /** One read for the rounds of every parent in the level, whatever type the parent is. */
    private <P> Map<P, RoundView> rounds(List<P> parents, Function<P, String> roundId) {
        Map<RoundId, Round> rounds = queryRounds.byIds(
                parents.stream().map(parent -> RoundId.of(roundId.apply(parent))).distinct().toList());
        return byKey(parents, parent -> RoundView.from(rounds.get(RoundId.of(roundId.apply(parent)))));
    }

    /** Two equal views (the same parent reached twice in one level) are one key: the first one is kept. */
    private static <P, C> Map<P, C> byKey(List<P> parents, Function<P, C> child) {
        Map<P, C> byParent = new LinkedHashMap<>();
        parents.forEach(parent -> byParent.computeIfAbsent(parent, child));
        return byParent;
    }
}
