package com.roboleague.api.graphql;

import com.roboleague.api.graphql.view.AppealView;
import com.roboleague.api.graphql.view.AttemptView;
import com.roboleague.api.graphql.view.RoundResultView;
import com.roboleague.api.graphql.view.ScoreBreakdownView;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.port.in.AttemptBreakdown;
import com.roboleague.port.in.GetAttemptBreakdown;
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
 * Resolves {@code Query.attempt}, the breakdown of an attempt and the relations that lead to an attempt. Same use
 * case as {@code GET /attempts/{id}/breakdown}; this adapter only translates. Whoever loads an attempt here loads it
 * with its breakdown, so {@code attempt { breakdown }} costs no extra read; only the attempts that arrive without it
 * ({@code Turn.attempts}, {@code Team.results}) are explained in one batch per level.
 */
@Controller
class AttemptQueries {

    private final GetAttemptBreakdown breakdowns;

    AttemptQueries(GetAttemptBreakdown breakdowns) {
        this.breakdowns = Objects.requireNonNull(breakdowns);
    }

    /** An id without the {@code <slot>-<number>} shape is an IllegalArgumentException: BAD_REQUEST. */
    @QueryMapping
    AttemptView attempt(@Argument String id) {
        return AttemptView.from(breakdowns.execute(AttemptId.parse(id)));
    }

    /** The views that already carry their breakdown answer as they are; the rest share one batch. */
    @BatchMapping(typeName = "Attempt", field = "breakdown")
    Map<AttemptView, ScoreBreakdownView> breakdown(List<AttemptView> attempts) {
        Map<AttemptId, AttemptBreakdown> loaded = breakdowns.byIds(attempts.stream()
                .filter(attempt -> attempt.loadedBreakdown().isEmpty())
                .map(attempt -> AttemptId.parse(attempt.id())).distinct().toList());
        return byKey(attempts, attempt -> attempt.loadedBreakdown()
                .orElseGet(() -> ScoreBreakdownView.from(loaded.get(AttemptId.parse(attempt.id())))));
    }

    @BatchMapping(typeName = "RoundResult", field = "attempt")
    Map<RoundResultView, AttemptView> roundResultAttempt(List<RoundResultView> results) {
        return attempts(results, RoundResultView::attemptId);
    }

    @BatchMapping(typeName = "Appeal", field = "attempt")
    Map<AppealView, AttemptView> appealAttempt(List<AppealView> appeals) {
        return attempts(appeals, AppealView::attemptId);
    }

    /** One batch for the attempts of every parent in the level, each view built with its breakdown. */
    private <P> Map<P, AttemptView> attempts(List<P> parents, Function<P, String> attemptId) {
        Map<AttemptId, AttemptBreakdown> loaded = breakdowns.byIds(
                parents.stream().map(parent -> AttemptId.parse(attemptId.apply(parent))).distinct().toList());
        return byKey(parents, parent -> AttemptView.from(loaded.get(AttemptId.parse(attemptId.apply(parent)))));
    }

    /** Two equal views (the same parent reached twice in one level) are one key: the first one is kept. */
    private static <P, C> Map<P, C> byKey(List<P> parents, Function<P, C> child) {
        Map<P, C> byParent = new LinkedHashMap<>();
        parents.forEach(parent -> byParent.computeIfAbsent(parent, child));
        return byParent;
    }
}
