package com.roboleague.evaluation.audit;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreBreakdown;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Event triggered whenever an attempt score is adjusted and recalculated.
 */
public record ScoreAdjustedEvent(
        EventMetadata metadata,
        EvaluationSnapshot evaluation,
        ScoreAdjustmentDetails details
) implements AttemptEvent {

    public ScoreAdjustedEvent {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        Objects.requireNonNull(evaluation, "evaluation cannot be null");
        Objects.requireNonNull(details, "details cannot be null");
    }

    @Override
    public String eventId() {
        return metadata.eventId();
    }

    @Override
    public String attemptId() {
        return metadata.attemptId();
    }

    @Override
    public LocalDateTime timestamp() {
        return metadata.timestamp();
    }

    public int newRevisionNumber() {
        return details.newRevisionNumber();
    }

    public String reason() {
        return details.reason();
    }

    public String authorId() {
        return details.authorId();
    }

    public RawMetrics newMetrics() {
        return evaluation.metrics();
    }

    public ScoreBreakdown newScoreBreakdown() {
        return evaluation.breakdown();
    }

    @Override
    public String eventType() {
        return "SCORE_ADJUSTED";
    }

    @Override
    public String description() {
        return "Score adjusted (rev " + newRevisionNumber() + ") to " + newScoreBreakdown().totalScore() + ": " + reason();
    }

    public static ScoreAdjustedEvent create(String attemptId, int newRevisionNumber, RawMetrics newMetrics,
                                            ScoreBreakdown newBreakdown, String reason, String authorId) {
        return new ScoreAdjustedEvent(
                EventMetadata.create(attemptId),
                EvaluationSnapshot.of(newMetrics, newBreakdown),
                ScoreAdjustmentDetails.of(newRevisionNumber, reason, authorId)
        );
    }
}
