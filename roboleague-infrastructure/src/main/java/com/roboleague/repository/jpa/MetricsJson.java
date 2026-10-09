package com.roboleague.repository.jpa;

import com.roboleague.evaluation.EvaluationFeedback;
import com.roboleague.evaluation.RawMetrics;
import com.roboleague.evaluation.TrackPerformance;
import com.roboleague.scheduling.JudgeId;

import java.util.Map;
import java.util.stream.Collectors;

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
                metrics.judgeSubjectiveScores().entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().value(), Map.Entry::getValue)),
                metrics.customMetrics());
    }

    Map<JudgeId, Double> typedJudgeScores() {
        return judgeSubjectiveScores.entrySet().stream()
                .collect(Collectors.toMap(e -> JudgeId.of(e.getKey()), Map.Entry::getValue));
    }

    RawMetrics toMetrics() {
        return RawMetrics.of(
                TrackPerformance.of(timeTakenSeconds, objectivesCompleted, penaltiesCount),
                EvaluationFeedback.of(resourceConsumption, typedJudgeScores(), customMetrics));
    }
}
