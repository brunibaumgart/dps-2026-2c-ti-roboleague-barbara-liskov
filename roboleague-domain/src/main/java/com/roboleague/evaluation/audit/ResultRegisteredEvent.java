package com.roboleague.evaluation.audit;

import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreBreakdown;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Event triggered when an attempt result is first captured and registered.
 */
public record ResultRegisteredEvent(
        EventMetadata metadata,
        EvaluationSnapshot evaluation,
        TeamJudgeBinding binding
) implements AttemptEvent {

    public ResultRegisteredEvent {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        Objects.requireNonNull(evaluation, "evaluation cannot be null");
        Objects.requireNonNull(binding, "binding cannot be null");
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

    public String teamId() {
        return binding.teamId();
    }

    public String judgeId() {
        return binding.judgeId();
    }

    public RawMetrics metrics() {
        return evaluation.metrics();
    }

    public ScoreBreakdown scoreBreakdown() {
        return evaluation.breakdown();
    }

    @Override
    public String eventType() {
        return "RESULT_REGISTERED";
    }

    @Override
    public String description() {
        return "Initial result registered by judge " + judgeId() + " with score " + scoreBreakdown().totalScore();
    }

    public static ResultRegisteredEvent create(String attemptId, String teamId, RawMetrics metrics, ScoreBreakdown breakdown, String judgeId) {
        return new ResultRegisteredEvent(
                EventMetadata.create(attemptId),
                EvaluationSnapshot.of(metrics, breakdown),
                TeamJudgeBinding.of(teamId, judgeId)
        );
    }
}
