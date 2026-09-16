package com.roboleague.evaluation.audit;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Event triggered when a post-review penalty is applied to an attempt.
 */
public record PenaltyAppliedEvent(
        EventMetadata metadata,
        PenaltyDetail detail,
        String judgeId
) implements AttemptEvent {

    public PenaltyAppliedEvent {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        Objects.requireNonNull(detail, "detail cannot be null");
        Objects.requireNonNull(judgeId, "judgeId cannot be null");
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

    public int additionalPenalties() {
        return detail.additionalPenalties();
    }

    public String reason() {
        return detail.reason();
    }

    @Override
    public String eventType() {
        return "PENALTY_APPLIED";
    }

    @Override
    public String description() {
        return "Penalty of " + additionalPenalties() + " fouls applied by " + judgeId + ": " + reason();
    }

    public static PenaltyAppliedEvent create(String attemptId, int additionalPenalties, String reason, String judgeId) {
        return new PenaltyAppliedEvent(
                EventMetadata.create(attemptId),
                PenaltyDetail.of(additionalPenalties, reason),
                judgeId
        );
    }
}
