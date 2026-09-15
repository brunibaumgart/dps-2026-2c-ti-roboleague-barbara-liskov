package com.roboleague.evaluation.audit;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreBreakdown;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable snapshot of an attempt score revision.
 * Preserves the exact state, metrics, breakdown, timestamp and author.
 */
public record AttemptScoreSnapshot(
        String snapshotId,
        int revisionNumber,
        LocalDateTime timestamp,
        String authorOrJudgeId,
        RawMetrics metrics,
        ScoreBreakdown breakdown,
        String reason
) {
    public AttemptScoreSnapshot {
        Objects.requireNonNull(snapshotId, "snapshotId cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(authorOrJudgeId, "authorOrJudgeId cannot be null");
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(breakdown, "breakdown cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
    }
}
