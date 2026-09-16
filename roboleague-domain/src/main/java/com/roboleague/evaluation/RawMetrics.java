package com.roboleague.evaluation;

import java.util.Map;
import java.util.Objects;

/**
 * Raw metrics captured during an attempt on the track/arena.
 */
public record RawMetrics(
        TrackPerformance performance,
        EvaluationFeedback feedback
) {
    public RawMetrics {
        Objects.requireNonNull(performance, "performance cannot be null");
        feedback = feedback != null ? feedback : EvaluationFeedback.empty();
    }

    public double timeTakenSeconds() {
        return performance.timeTakenSeconds();
    }

    public int objectivesCompleted() {
        return performance.objectivesCompleted();
    }

    public int penaltiesCount() {
        return performance.penaltiesCount();
    }

    public double resourceConsumption() {
        return feedback.resourceConsumption();
    }

    public Map<String, Double> judgeSubjectiveScores() {
        return feedback.judgeSubjectiveScores();
    }

    public Map<String, Double> customMetrics() {
        return feedback.customMetrics();
    }

    public double getAverageJudgeScore() {
        return feedback.averageJudgeScore();
    }

    public static RawMetrics of(TrackPerformance performance, EvaluationFeedback feedback) {
        return new RawMetrics(performance, feedback);
    }

    public static RawMetrics of(double timeTakenSeconds, int objectivesCompleted, int penaltiesCount) {
        return new RawMetrics(
                new TrackPerformance(timeTakenSeconds, objectivesCompleted, penaltiesCount),
                EvaluationFeedback.empty()
        );
    }

    public static RawMetrics of(double timeTakenSeconds, int objectivesCompleted, int penaltiesCount, Map<String, Double> judgeScores) {
        return new RawMetrics(
                new TrackPerformance(timeTakenSeconds, objectivesCompleted, penaltiesCount),
                EvaluationFeedback.withJudgeScores(judgeScores)
        );
    }
}
