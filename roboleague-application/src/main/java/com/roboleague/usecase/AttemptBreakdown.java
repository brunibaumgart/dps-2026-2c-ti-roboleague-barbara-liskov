package com.roboleague.usecase;

import com.roboleague.evaluation.Attempt;
import com.roboleague.evaluation.ResultSource;
import com.roboleague.evaluation.SourceContribution;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An attempt explained with its rulebook: the sources it still awaits and what each source contributed.
 */
public record AttemptBreakdown(Attempt attempt, Set<ResultSource> awaitedSources, List<SourceContribution> bySource) {
    public AttemptBreakdown {
        Objects.requireNonNull(attempt, "attempt cannot be null");
        awaitedSources = Set.copyOf(awaitedSources);
        bySource = List.copyOf(bySource);
    }
}
