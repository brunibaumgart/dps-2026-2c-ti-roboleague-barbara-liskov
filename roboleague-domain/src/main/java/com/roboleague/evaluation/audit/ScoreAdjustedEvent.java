package com.roboleague.evaluation.audit;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreBreakdown;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event triggered whenever an attempt score is adjusted and recalculated.
 */
public record ScoreAdjustedEvent(
        String eventId,
        String attemptId,
        int newRevisionNumber,
        LocalDateTime timestamp,
        RawMetrics newMetrics,
        ScoreBreakdown newScoreBreakdown,
        String reason,
        String authorId
) implements AttemptEvent {

    public static ScoreAdjustedEvent create(String attemptId, int newRevisionNumber, RawMetrics newMetrics,
                                            ScoreBreakdown newBreakdown, String reason, String authorId) {
        return new ScoreAdjustedEvent(
                UUID.randomUUID().toString(),
                attemptId,
                newRevisionNumber,
                LocalDateTime.now(),
                newMetrics,
                newBreakdown,
                reason,
                authorId
        );
    }

    @Override
    public String eventType() {
        return "SCORE_ADJUSTED";
    }

    @Override
    public String description() {
        return "Score adjusted (rev " + newRevisionNumber + ") to " + newScoreBreakdown.totalScore() + ": " + reason;
    }
}
