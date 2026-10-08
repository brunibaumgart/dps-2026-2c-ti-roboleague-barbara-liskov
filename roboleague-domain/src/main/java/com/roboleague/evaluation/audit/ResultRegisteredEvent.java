package com.roboleague.evaluation.audit;

import com.roboleague.evaluation.AttemptId;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.ScoreBreakdown;
import com.roboleague.scheduling.JudgeId;
import com.roboleague.tournament.TeamId;

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
    public AttemptId attemptId() {
        return metadata.attemptId();
    }

    @Override
    public LocalDateTime timestamp() {
        return metadata.timestamp();
    }

    public TeamId teamId() {
        return binding.teamId();
    }

    public JudgeId judgeId() {
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

    public static ResultRegisteredEvent create(EventMetadata metadata, TeamId teamId, EvaluationSnapshot evaluation, JudgeId judgeId) {
        return new ResultRegisteredEvent(
                metadata,
                evaluation,
                TeamJudgeBinding.of(teamId, judgeId)
        );
    }
}
