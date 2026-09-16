package com.roboleague.evaluation.audit;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Event triggered when an appeal is formally accepted by the arbitration committee.
 */
public record AppealAcceptedEvent(
        EventMetadata metadata,
        AppealResolution resolution,
        String reviewerId
) implements AttemptEvent {

    public AppealAcceptedEvent {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        Objects.requireNonNull(resolution, "resolution cannot be null");
        Objects.requireNonNull(reviewerId, "reviewerId cannot be null");
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

    public String appealId() {
        return resolution.appealId();
    }

    public String resolutionNotes() {
        return resolution.resolutionNotes();
    }

    @Override
    public String eventType() {
        return "APPEAL_ACCEPTED";
    }

    @Override
    public String description() {
        return "Appeal " + appealId() + " accepted by " + reviewerId + ": " + resolutionNotes();
    }

    public static AppealAcceptedEvent create(String attemptId, String appealId, String resolutionNotes, String reviewerId) {
        return new AppealAcceptedEvent(
                EventMetadata.create(attemptId),
                AppealResolution.of(appealId, resolutionNotes),
                reviewerId
        );
    }
}
