package com.roboleague.evaluation.audit;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event triggered when a post-review penalty is applied to an attempt.
 */
public record PenaltyAppliedEvent(
        String eventId,
        String attemptId,
        LocalDateTime timestamp,
        int additionalPenalties,
        String reason,
        String judgeId
) implements AttemptEvent {

    public static PenaltyAppliedEvent create(String attemptId, int additionalPenalties, String reason, String judgeId) {
        return new PenaltyAppliedEvent(
                UUID.randomUUID().toString(),
                attemptId,
                LocalDateTime.now(),
                additionalPenalties,
                reason,
                judgeId
        );
    }

    @Override
    public String eventType() {
        return "PENALTY_APPLIED";
    }

    @Override
    public String description() {
        return "Penalty of " + additionalPenalties + " fouls applied by " + judgeId + ": " + reason;
    }
}
