package com.roboleague.repository.jpa;

import com.roboleague.evaluation.EvaluationFeedback;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.TrackPerformance;

import java.util.Map;

/**
 * Raw metrics as stored in a JSON column, by appeals and attempts alike.
 */
record MetricsJson(double timeTakenSeconds,
                   int objectivesCompleted,
                   int penaltiesCount,
                   double resourceConsumption,
                   Map<String, Double> judgeSubjectiveScores,
                   Map<String, Double> customMetrics) {

    static MetricsJson of(RawMetrics metrics) {
        return new MetricsJson(
                metrics.timeTakenSeconds(),
                metrics.objectivesCompleted(),
                metrics.penaltiesCount(),
                metrics.resourceConsumption(),
                metrics.judgeSubjectiveScores(),
                metrics.customMetrics());
    }

    RawMetrics toMetrics() {
        return RawMetrics.of(
                TrackPerformance.of(timeTakenSeconds, objectivesCompleted, penaltiesCount),
                EvaluationFeedback.of(resourceConsumption, judgeSubjectiveScores, customMetrics));
    }
}
