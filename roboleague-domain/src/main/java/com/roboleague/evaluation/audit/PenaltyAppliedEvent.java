package com.roboleague.evaluation.audit;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.support.ActorId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Event triggered when a post-review penalty is applied to an attempt.
 */
public record PenaltyAppliedEvent(
        EventMetadata metadata,
        PenaltyDetail detail,
        ActorId authorId
) implements AttemptEvent {

    public PenaltyAppliedEvent {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        Objects.requireNonNull(detail, "detail cannot be null");
        Objects.requireNonNull(authorId, "authorId cannot be null");
    }

    @Override
    public String eventId() {
        return metadata.eventId();
    }

    @Override
    public AttemptId attemptId() {
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
        return "Penalty of " + additionalPenalties() + " fouls applied by " + authorId + ": " + reason();
    }

    public static PenaltyAppliedEvent create(EventMetadata metadata, int additionalPenalties, String reason, ActorId authorId) {
        return new PenaltyAppliedEvent(
                metadata,
                PenaltyDetail.of(additionalPenalties, reason),
                authorId
        );
    }
}
