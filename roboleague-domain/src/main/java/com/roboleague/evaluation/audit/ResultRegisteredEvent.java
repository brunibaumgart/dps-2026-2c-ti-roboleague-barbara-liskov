package com.roboleague.evaluation.audit;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreBreakdown;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event triggered when an attempt result is first captured and registered.
 */
public record ResultRegisteredEvent(
        String eventId,
        String attemptId,
        String teamId,
        LocalDateTime timestamp,
        RawMetrics metrics,
        ScoreBreakdown scoreBreakdown,
        String judgeId
) implements AttemptEvent {

    public static ResultRegisteredEvent create(String attemptId, String teamId, RawMetrics metrics, ScoreBreakdown breakdown, String judgeId) {
        return new ResultRegisteredEvent(
                UUID.randomUUID().toString(),
                attemptId,
                teamId,
                LocalDateTime.now(),
                metrics,
                breakdown,
                judgeId
        );
    }

    @Override
    public String eventType() {
        return "RESULT_REGISTERED";
    }

    @Override
    public String description() {
        return "Initial result registered by judge " + judgeId + " with score " + scoreBreakdown.totalScore();
    }
}
