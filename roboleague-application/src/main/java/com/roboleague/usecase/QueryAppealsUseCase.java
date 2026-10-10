package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.AttemptId;
import com.roboleague.ranking.StandingsId;
import com.roboleague.ranking.appeal.Appeal;
import com.roboleague.ranking.appeal.AppealId;
import com.roboleague.repository.AppealRepository;
import com.roboleague.repository.AttemptRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Appeals by id, of one attempt, or of every attempt a challenge ran in a category; oldest first.
 */
public class QueryAppealsUseCase {
    private static final Comparator<Appeal> FILING_ORDER = Comparator.comparing(Appeal::getSubmittedAt)
            .thenComparing(appeal -> appeal.getAppealId().value());

    private final AppealRepository appeals;
    private final AttemptRepository attempts;
    private final CategoryResultsReader results;

    public QueryAppealsUseCase(AppealRepository appeals, AttemptRepository attempts, CategoryResultsReader results) {
        this.appeals = Objects.requireNonNull(appeals, "appeals cannot be null");
        this.attempts = Objects.requireNonNull(attempts, "attempts cannot be null");
        this.results = Objects.requireNonNull(results, "results cannot be null");
    }

    public Appeal get(AppealId id) {
        return appeals.findById(id).orElseThrow(() -> new IllegalArgumentException("Appeal not found: " + id));
    }

    public List<Appeal> ofAttempt(AttemptId attemptId) {
        attempts.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + attemptId));
        return appeals.findByAttemptId(attemptId).stream().sorted(FILING_ORDER).toList();
    }

    public List<Appeal> ofCategory(StandingsId id) {
        return results.read(id).attempts().stream()
                .map(Attempt::getId)
                .flatMap(attemptId -> appeals.findByAttemptId(attemptId).stream())
                .sorted(FILING_ORDER)
                .toList();
    }
}
