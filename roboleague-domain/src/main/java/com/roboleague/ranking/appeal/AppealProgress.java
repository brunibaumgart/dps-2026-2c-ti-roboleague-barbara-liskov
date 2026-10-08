package com.roboleague.ranking.appeal;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.support.ActorId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * How far an appeal has gone through its lifecycle. Used to restore a stored appeal.
 */
public record AppealProgress(
        AppealState state,
        LocalDateTime submittedAt,
        ActorId reviewerId,
        String resolutionNotes,
        RawMetrics revisedMetrics,
        LocalDateTime resolvedAt
) {
    public AppealProgress {
        Objects.requireNonNull(state, "state cannot be null");
        Objects.requireNonNull(submittedAt, "submittedAt cannot be null");
    }
}
