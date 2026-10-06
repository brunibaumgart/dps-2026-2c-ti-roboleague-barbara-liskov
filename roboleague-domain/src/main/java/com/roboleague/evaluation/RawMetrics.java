package com.roboleague.evaluation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

    public double measurement(Metric metric) {
        Double value = feedback.customMetrics().get(metric.name());
        if (value == null) {
            throw new IllegalStateException("measurement '" + metric.name() + "' was not captured");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("measurement '" + metric.name() + "' must be a finite number: " + value);
        }
        return value;
    }

    public int count(Metric metric) {
        double value = measurement(metric);
        if (value < 0 || value != Math.rint(value)) {
            throw new IllegalArgumentException("measurement '" + metric.name() + "' must be a whole count: " + value);
        }
        return (int) value;
    }

    /**
     * Nothing measured yet: what the sources of an attempt add their results to.
     */
    public static RawMetrics nothingMeasured() {
        return new RawMetrics(new TrackPerformance(0.0, 0, 0), EvaluationFeedback.empty());
    }

    /**
     * The capture with what the track's sensors measured: time, objectives, faults, consumption and their named
     * measurements. What the judges sent stays.
     */
    public RawMetrics withTrack(TrackPerformance track, double consumption, Map<String, Double> named) {
        return new RawMetrics(track, EvaluationFeedback.of(consumption, judgeSubjectiveScores(), measuredWith(named)));
    }

    /**
     * The capture with what the judge panel sent: each judge's score and their named measurements. What the
     * sensors measured stays.
     */
    public RawMetrics withJudgePanel(Map<String, Double> scores, Map<String, Double> named) {
        return new RawMetrics(performance, EvaluationFeedback.of(resourceConsumption(), scores, measuredWith(named)));
    }

    private Map<String, Double> measuredWith(Map<String, Double> named) {
        Map<String, Double> measured = new HashMap<>(customMetrics());
        measured.putAll(named);
        return measured;
    }

    /**
     * The same capture with another fault count: everything else that was measured stays as it was.
     */
    public RawMetrics withPenalties(int penaltiesCount) {
        return new RawMetrics(new TrackPerformance(timeTakenSeconds(), objectivesCompleted(), penaltiesCount), feedback);
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
