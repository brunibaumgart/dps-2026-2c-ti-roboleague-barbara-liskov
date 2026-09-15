package com.roboleague.evaluation.audit;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event triggered when an appeal is formally accepted by the arbitration committee.
 */
public record AppealAcceptedEvent(
        String eventId,
        String attemptId,
        String appealId,
        LocalDateTime timestamp,
        String resolutionNotes,
        String reviewerId
) implements AttemptEvent {

    public static AppealAcceptedEvent create(String attemptId, String appealId, String resolutionNotes, String reviewerId) {
        return new AppealAcceptedEvent(
                UUID.randomUUID().toString(),
                attemptId,
                appealId,
                LocalDateTime.now(),
                resolutionNotes,
                reviewerId
        );
    }

    @Override
    public String eventType() {
        return "APPEAL_ACCEPTED";
    }

    @Override
    public String description() {
        return "Appeal " + appealId + " accepted by " + reviewerId + ": " + resolutionNotes;
    }
}
