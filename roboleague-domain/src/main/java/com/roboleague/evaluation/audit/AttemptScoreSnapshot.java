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
        SnapshotMetadata metadata,
        EvaluationSnapshot evaluation,
        String reason
) {
    public AttemptScoreSnapshot {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        Objects.requireNonNull(evaluation, "evaluation cannot be null");
        reason = reason != null ? reason : "";
    }

    public String snapshotId() {
        return metadata.snapshotId();
    }

    public int revisionNumber() {
        return metadata.revisionNumber();
    }

    public LocalDateTime timestamp() {
        return metadata.timestamp();
    }

    public String authorOrJudgeId() {
        return metadata.authorOrJudgeId();
    }

    public RawMetrics metrics() {
        return evaluation.metrics();
    }

    public ScoreBreakdown breakdown() {
        return evaluation.breakdown();
    }

    public static AttemptScoreSnapshot of(SnapshotMetadata metadata, EvaluationSnapshot evaluation, String reason) {
        return new AttemptScoreSnapshot(metadata, evaluation, reason);
    }

    public static AttemptScoreSnapshot of(String snapshotId, int revisionNumber, String authorOrJudgeId,
                                          RawMetrics metrics, ScoreBreakdown breakdown, String reason) {
        return new AttemptScoreSnapshot(
                SnapshotMetadata.of(snapshotId, revisionNumber, authorOrJudgeId),
                EvaluationSnapshot.of(metrics, breakdown),
                reason
        );
    }
}
